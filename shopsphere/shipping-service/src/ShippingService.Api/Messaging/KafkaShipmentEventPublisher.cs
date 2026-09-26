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

    public async Task PublishDispatchedAsync(ShipmentDispatchedEvent dispatched, CancellationToken cancellationToken)
    {
        var message = new Message<string, string>
        {
            Key = dispatched.OrderId.ToString(CultureInfo.InvariantCulture), // same partitioning key as order/payment events
            Value = EventJson.Serialize(dispatched),
            Headers = new Headers { { "eventType", Encoding.UTF8.GetBytes(dispatched.EventType) } }
        };

        var result = await _producer.ProduceAsync(Topics.ShipmentDispatched, message, cancellationToken);

        _logger.LogInformation(
            "Published shipment.dispatched eventId={EventId} orderId={OrderId} tracking={TrackingNumber} to {TopicPartitionOffset}",
            dispatched.EventId, dispatched.OrderId, dispatched.TrackingNumber, result.TopicPartitionOffset);
    }

    public void Dispose()
    {
        _producer.Flush(TimeSpan.FromSeconds(5));
        _producer.Dispose();
    }
}
