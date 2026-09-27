namespace ShippingService.Api.Domain;

/// <summary>
/// One shipment per order. Lifecycle: PENDING → SHIPPED → DELIVERED, or PENDING → CANCELLED.
/// Timestamps (CreatedAt/UpdatedAt) are stamped by ShippingDbContext on save.
/// *PublishedAt columns form a small outbox: null means the event still has to go out.
/// </summary>
public sealed class Shipment
{
    private Shipment() { } // EF Core

    public long Id { get; private set; }
    public long OrderId { get; private set; }
    public long UserId { get; private set; }
    public ShipmentStatus Status { get; private set; }
    public string? Carrier { get; private set; }
    public string? TrackingNumber { get; private set; }
    public ShippingAddress ShippingAddress { get; private set; } = null!;
    public DateTimeOffset CreatedAt { get; private set; }
    public DateTimeOffset UpdatedAt { get; private set; }
    public DateTimeOffset? ShippedAt { get; private set; }
    public DateTimeOffset? DeliveredAt { get; private set; }

    /// <summary>US35 — set once shipment.dispatched has been acknowledged by Kafka.</summary>
    public DateTimeOffset? DispatchPublishedAt { get; private set; }

    /// <summary>US36 — set once shipment.delivered has been acknowledged by Kafka.</summary>
    public DateTimeOffset? DeliveredPublishedAt { get; private set; }

    public static Shipment Create(long orderId, long userId, ShippingAddress shippingAddress)
    {
        ArgumentOutOfRangeException.ThrowIfNegativeOrZero(orderId);
        ArgumentOutOfRangeException.ThrowIfNegativeOrZero(userId);
        ArgumentNullException.ThrowIfNull(shippingAddress);

        return new Shipment
        {
            OrderId = orderId,
            UserId = userId,
            ShippingAddress = shippingAddress,
            Status = ShipmentStatus.Pending
        };
    }

    public void MarkShipped(string carrier, string trackingNumber, DateTimeOffset shippedAt)
    {
        EnsureStatus(ShipmentStatus.Pending, "ship");
        ArgumentException.ThrowIfNullOrWhiteSpace(carrier);
        ArgumentException.ThrowIfNullOrWhiteSpace(trackingNumber);

        Carrier = carrier.Trim();
        TrackingNumber = trackingNumber.Trim();
        ShippedAt = shippedAt;
        Status = ShipmentStatus.Shipped;
    }

    public void MarkDispatchPublished(DateTimeOffset publishedAt)
    {
        if (Status != ShipmentStatus.Shipped && Status != ShipmentStatus.Delivered)
        {
            throw new InvalidOperationException(
                $"Cannot record dispatch publication for shipment {Id} in status {Status}.");
        }
        DispatchPublishedAt ??= publishedAt;
    }

    public void MarkDelivered(DateTimeOffset deliveredAt)
    {
        EnsureStatus(ShipmentStatus.Shipped, "deliver");
        DeliveredAt = deliveredAt;
        Status = ShipmentStatus.Delivered;
    }

    public void MarkDeliveredPublished(DateTimeOffset publishedAt)
    {
        if (Status != ShipmentStatus.Delivered)
        {
            throw new InvalidOperationException(
                $"Cannot record delivery publication for shipment {Id} in status {Status}.");
        }
        DeliveredPublishedAt ??= publishedAt;
    }

    public void Cancel()
    {
        EnsureStatus(ShipmentStatus.Pending, "cancel");
        Status = ShipmentStatus.Cancelled;
    }

    private void EnsureStatus(ShipmentStatus expected, string action)
    {
        if (Status != expected)
        {
            throw new InvalidOperationException(
                $"Cannot {action} shipment {Id} in status {Status}; expected {expected}.");
        }
    }
}
