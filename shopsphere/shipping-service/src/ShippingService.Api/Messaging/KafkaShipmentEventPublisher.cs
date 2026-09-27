using System.Globalization;
using System.Text;
using Confluent.Kafka;
using Microsoft.Extensions.Options;
using ShippingService.Api.Messaging.Events;
using ShippingService.Api.Options;

namespace ShippingService.Api.Messaging;

public sealed class KafkaShipmentEventPublisher : IShipmentEventPublisher, IDisposable
{
    private readonly IProducer<string, string> _producer;
    private readonly ILogger<KafkaShipmentEventPublisher> _logger;

    public KafkaShipmentEventPublisher(IOptions<KafkaOptions> options, ILogger<KafkaShipmentEventPublisher> logger)
    {
        _logger = logger;
        var config = new ProducerConfig
        {
            BootstrapServers = options.Value.BootstrapServers,
            Acks = Acks.All,                 // same durability as the Java producers
            EnableIdempotence = true,        // no duplicates from producer retries
            LingerMs = 5,
            MessageTimeoutMs = 30_000,
            ClientId = "shipping-service"
        };
        _producer = new ProducerBuilder<string, string>(config)
            .SetErrorHandler((_, e) => _logger.LogWarning("Kafka producer error: {Reason} (fatal={IsFatal})", e.Reason, e.IsFatal))
            .Build();
    }

    public Task PublishDispatchedAsync(ShipmentDispatchedEvent dispatched, CancellationToken cancellationToken) =>
        ProduceAsync(Topics.ShipmentDispatched, dispatched.OrderId, dispatched.EventId, dispatched.EventType,
            EventJson.Serialize(dispatched), cancellationToken);

    public Task PublishDeliveredAsync(ShipmentDeliveredEvent delivered, CancellationToken cancellationToken) =>
        ProduceAsync(Topics.ShipmentDelivered, delivered.OrderId, delivered.EventId, delivered.EventType,
            EventJson.Serialize(delivered), cancellationToken);

    private async Task ProduceAsync(string topic, long orderId, string eventId, string eventType, string payload,
        CancellationToken cancellationToken)
    {
        var message = new Message<string, string>
        {
            Key = orderId.ToString(CultureInfo.InvariantCulture), // same partitioning key as order/payment events
            Value = payload,
            Headers = new Headers { { "eventType", Encoding.UTF8.GetBytes(eventType) } }
        };

        var result = await _producer.ProduceAsync(topic, message, cancellationToken);

        _logger.LogInformation("Published {EventType} eventId={EventId} orderId={OrderId} to {TopicPartitionOffset}",
            eventType, eventId, orderId, result.TopicPartitionOffset);
    }

    public void Dispose()
    {
        _producer.Flush(TimeSpan.FromSeconds(5));
        _producer.Dispose();
    }
}
