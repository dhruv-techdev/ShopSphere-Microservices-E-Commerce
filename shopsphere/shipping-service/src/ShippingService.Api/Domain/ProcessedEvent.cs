namespace ShippingService.Api.Domain;

/// <summary>
/// Records a consumed Kafka event so redeliveries of the same EventId are skipped (idempotent consumer).
/// </summary>
public sealed class ProcessedEvent
{
    public const int EventIdMaxLength = 100;
    public const int EventTypeMaxLength = 100;

    private ProcessedEvent() { } // EF Core

    public string EventId { get; private set; } = null!;
    public string EventType { get; private set; } = null!;
    public DateTimeOffset ProcessedAt { get; private set; }

    public static ProcessedEvent Create(string eventId, string eventType, DateTimeOffset processedAt)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(eventId);
        ArgumentException.ThrowIfNullOrWhiteSpace(eventType);
        ArgumentOutOfRangeException.ThrowIfGreaterThan(eventId.Length, EventIdMaxLength);
        ArgumentOutOfRangeException.ThrowIfGreaterThan(eventType.Length, EventTypeMaxLength);

        return new ProcessedEvent
        {
            EventId = eventId,
            EventType = eventType,
            ProcessedAt = processedAt
        };
    }
}
