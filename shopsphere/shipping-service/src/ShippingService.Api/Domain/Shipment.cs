namespace ShippingService.Api.Domain;

/// <summary>
/// One shipment per order. Lifecycle: PENDING → SHIPPED → DELIVERED, or PENDING → CANCELLED.
/// Timestamps (CreatedAt/UpdatedAt) are stamped by ShippingDbContext on save.
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

    public void MarkDelivered(DateTimeOffset deliveredAt)
    {
        EnsureStatus(ShipmentStatus.Shipped, "deliver");
        DeliveredAt = deliveredAt;
        Status = ShipmentStatus.Delivered;
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
