using Microsoft.Extensions.Options;
using ShippingService.Api.Options;
using ShippingService.Api.Shipping;

namespace ShippingService.Api.Messaging;

/// <summary>Periodically re-publishes shipment events that failed to go out.</summary>
public sealed class ShipmentOutboxRelay(
    IServiceScopeFactory scopeFactory,
    IOptions<KafkaOptions> kafkaOptions,
    ILogger<ShipmentOutboxRelay> logger) : BackgroundService
{
    private const int BatchSize = 100;

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        var interval = TimeSpan.FromSeconds(Math.Max(5, kafkaOptions.Value.OutboxRelayIntervalSeconds));
        using var timer = new PeriodicTimer(interval);

        try
        {
            while (await timer.WaitForNextTickAsync(stoppingToken))
            {
                try
                {
                    using var scope = scopeFactory.CreateScope();
                    var outbox = scope.ServiceProvider.GetRequiredService<ShipmentEventOutbox>();
                    var caughtUp = await outbox.RepublishPendingAsync(interval, BatchSize, stoppingToken);
                    if (caughtUp > 0)
                    {
                        logger.LogInformation("Outbox relay re-published events for {Count} shipment(s)", caughtUp);
                    }
                }
                catch (Exception ex) when (ex is not OperationCanceledException)
                {
                    logger.LogWarning(ex, "Outbox relay pass failed");
                }
            }
        }
        catch (OperationCanceledException) when (stoppingToken.IsCancellationRequested)
        {
            // shutting down
        }
    }
}
