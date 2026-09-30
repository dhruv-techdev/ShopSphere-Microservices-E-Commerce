using ShippingService.Api.Domain;

namespace ShippingService.Api.Contracts;

public sealed record AddressResponse(
    string RecipientName,
    string? Phone,
    string Line1,
    string? Line2,
    string City,
    string? State,
    string PostalCode,
    string Country)
{
    public static AddressResponse From(ShippingAddress a) =>
        new(a.RecipientName, a.Phone, a.Line1, a.Line2, a.City, a.State, a.PostalCode, a.Country);
}

public sealed record ShipmentResponse(
    long Id,
    long OrderId,
    long UserId,
    ShipmentStatus Status,
    string? Carrier,
    string? TrackingNumber,
    AddressResponse ShippingAddress,
    DateTimeOffset CreatedAt,
    DateTimeOffset UpdatedAt,
    DateTimeOffset? ShippedAt,
    DateTimeOffset? DeliveredAt)
{
    public static ShipmentResponse From(Shipment s) =>
        new(s.Id, s.OrderId, s.UserId, s.Status, s.Carrier, s.TrackingNumber,
            AddressResponse.From(s.ShippingAddress),
            s.CreatedAt, s.UpdatedAt, s.ShippedAt, s.DeliveredAt);
}
