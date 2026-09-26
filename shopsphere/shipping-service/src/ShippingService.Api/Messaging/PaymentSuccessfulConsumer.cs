using System.Text.Json;
using Confluent.Kafka;
using Microsoft.Extensions.Options;
using ShippingService.Api.Messaging.Events;
using ShippingService.Api.Options;
using ShippingService.Api.Shipping;

namespace ShippingService.Api.Messaging;

/// <summary>
/// Consumes payment.successful with manual offset commits: an offset is committed only after
/// the handler finished (shipment saved + dispatched event acknowledged). Transient failures
/// are retried with capped exponential backoff, blocking the partition rather than losing a
/// shipment. Poison messages (unparseable / invalid) are logged and skipped.
/// </summary>
public sealed class PaymentSuccessfulConsumer(
    IServiceScopeFactory scopeFactory,
    IOptions<KafkaOptions> kafkaOptions,
    ILogger<PaymentSuccessfulConsumer> logger) : BackgroundService
{
    private static readonly TimeSpan MaxBackoff = TimeSpan.FromSeconds(30);

    // Consume() is blocking, so run the loop on its own long-running thread.
    protected override Task ExecuteAsync(CancellationToken stoppingToken) =>
        Task.Factory.StartNew(
                () => ConsumeLoopAsync(stoppingToken),
                stoppingToken,
                TaskCreationOptions.LongRunning,
                TaskScheduler.Default)
            .Unwrap();

    private async Task ConsumeLoopAsync(CancellationToken ct)
    {
        var options = kafkaOptions.Value;
        var config = new ConsumerConfig
        {
            BootstrapServers = options.BootstrapServers,
            GroupId = options.GroupId,
            AutoOffsetReset = AutoOffsetReset.Earliest,
            EnableAutoCommit = false,
            AllowAutoCreateTopics = true,
            ClientId = "shipping-service"
        };

        using var consumer = new ConsumerBuilder<string, string>(config)
            .SetErrorHandler((_, e) => logger.LogWarning("Kafka consumer error: {Reason} (fatal={IsFatal})", e.Reason, e.IsFatal))
            .Build();

        consumer.Subscribe(Topics.PaymentSuccessful);
        logger.LogInformation("Subscribed to {Topic} as group {GroupId} on {Bootstrap}",
            Topics.PaymentSuccessful, options.GroupId, options.BootstrapServers);

        try
        {
            while (!ct.IsCancellationRequested)
            {
                ConsumeResult<string, string>? result;
                try
                {
                    result = consumer.Consume(ct);
                }
                catch (ConsumeException ex)
                {
                    logger.LogError(ex, "Kafka consume error: {Reason}", ex.Error.Reason);
                    continue;
                }

                if (result is null)
                {
                    continue;
                }

                if (result.Message?.Value is not null)
                {
                    await ProcessWithRetryAsync(result, ct);
                }

                consumer.Commit(result);
            }
        }
        catch (OperationCanceledException) when (ct.IsCancellationRequested)
        {
            // shutting down
        }
        finally
        {
            consumer.Close();
        }
    }

    private async Task ProcessWithRetryAsync(ConsumeResult<string, string> result, CancellationToken ct)
    {
        PaymentSuccessfulEvent evt;
        try
        {
            evt = EventJson.DeserializePaymentSuccessful(result.Message.Value);
        }
        catch (Exception ex) when (ex is JsonException or InvalidEventException)
        {
            logger.LogError(ex, "Skipping unreadable payment.successful at {TopicPartitionOffset}", result.TopicPartitionOffset);
            return;
        }

        for (var attempt = 1; ; attempt++)
        {
            ct.ThrowIfCancellationRequested();
            try
            {
                using var scope = scopeFactory.CreateScope();
                var handler = scope.ServiceProvider.GetRequiredService<PaymentSuccessfulHandler>();
                var outcome = await handler.HandleAsync(evt, ct);

                logger.LogInformation(
                    "payment.successful eventId={EventId} orderId={OrderId} -> {Outcome} ({TopicPartitionOffset})",
                    evt.EventId, evt.OrderId, outcome, result.TopicPartitionOffset);
                return;
            }
            catch (InvalidEventException ex)
            {
                logger.LogError(ex, "Skipping invalid payment.successful at {TopicPartitionOffset}", result.TopicPartitionOffset);
                return;
            }
            catch (Exception ex) when (ex is not OperationCanceledException)
            {
                var delay = TimeSpan.FromSeconds(Math.Min(MaxBackoff.TotalSeconds, Math.Pow(2, Math.Min(attempt, 5))));
                logger.LogWarning(ex,
                    "Attempt {Attempt} failed for payment.successful eventId={EventId} orderId={OrderId}; retrying in {Delay}",
                    attempt, evt.EventId, evt.OrderId, delay);
                await Task.Delay(delay, ct);
            }
        }
    }
}
