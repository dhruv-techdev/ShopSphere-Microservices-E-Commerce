namespace ShippingService.Api.Domain;

/// <summary>Stored and serialised upper case (PENDING, SHIPPED, ...) to match the Java services.</summary>
public enum ShipmentStatus
{
    Pending,
    Shipped,
    Delivered,
    Cancelled
}
