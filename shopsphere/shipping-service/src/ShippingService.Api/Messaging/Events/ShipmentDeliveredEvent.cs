using System.Security.Cryptography;
using System.Text;
using ShippingService.Api.Domain;

namespace ShippingService.Api.Messaging.Events;

/// <summary>Wire shape of common-lib ShipmentDeliveredEvent. UTC DateTime → ISO with 'Z'.</summary>
public sealed record ShipmentDeliveredEvent(
    string EventId,
    string EventType,
    DateTime OccurredAt,
    long ShipmentId,
    long OrderId,
    long UserId,
    string? Carrier,
    string? TrackingNumber,
    DateTime DeliveredAt)
{
    public const string Type = "shipment.delivered.v1";

    public static ShipmentDeliveredEvent From(Shipment shipment, DateTimeOffset occurredAt)
    {
        if (shipment.DeliveredAt is null)
        {
            throw new InvalidOperationException($"Shipment {shipment.Id} has not been delivered yet.");
        }

        return new ShipmentDeliveredEvent(
            EventIdFor(shipment.Id),
            Type,
            occurredAt.UtcDateTime,
            shipment.Id,
            shipment.OrderId,
            shipment.UserId,
            shipment.Carrier,
            shipment.TrackingNumber,
            shipment.DeliveredAt.Value.UtcDateTime);
    }

    /// <summary>Deterministic per shipment, so a re-publish carries the same id.</summary>
    public static string EventIdFor(long shipmentId) =>
        new Guid(MD5.HashData(Encoding.UTF8.GetBytes($"{Type}:{shipmentId}"))).ToString();
}
