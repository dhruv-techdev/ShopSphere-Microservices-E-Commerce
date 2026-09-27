namespace ShippingService.Api.Options;

public sealed class KafkaOptions
{
    public const string SectionName = "Kafka";

    /// <summary>When false the consumer and outbox relay are not started (tests, API-only runs).</summary>
    public bool Enabled { get; set; } = true;
    public string BootstrapServers { get; set; } = "localhost:9092";
    public string GroupId { get; set; } = "shipping-service";

    /// <summary>How often unpublished shipment events are retried. Minimum 5s.</summary>
    public int OutboxRelayIntervalSeconds { get; set; } = 30;
}
