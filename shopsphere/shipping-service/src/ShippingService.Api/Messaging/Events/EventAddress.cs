using ShippingService.Api.Domain;

namespace ShippingService.Api.Messaging.Events;

/// <summary>Wire shape of OrderCreatedEvent.ShippingAddress (common-lib).</summary>
public sealed record EventAddress(
    string? RecipientName,
    string? Phone,
    string? Line1,
    string? Line2,
    string? City,
    string? State,
    string? PostalCode,
    string? Country)
{
    public static EventAddress From(ShippingAddress a) =>
        new(a.RecipientName, a.Phone, a.Line1, a.Line2, a.City, a.State, a.PostalCode, a.Country);
}
