namespace ShippingService.Api.Options;

public sealed class KafkaOptions
{
    public const string SectionName = "Kafka";

    /// <summary>When false the payment.successful consumer is not started (tests, local API-only runs).</summary>
    public bool Enabled { get; set; } = true;
    public string BootstrapServers { get; set; } = "localhost:9092";
    public string GroupId { get; set; } = "shipping-service";
}
