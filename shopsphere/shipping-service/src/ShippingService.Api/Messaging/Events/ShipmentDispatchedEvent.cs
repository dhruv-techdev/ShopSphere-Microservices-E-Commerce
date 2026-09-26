using System.Security.Cryptography;
using System.Text;
using ShippingService.Api.Domain;

namespace ShippingService.Api.Messaging.Events;

/// <summary>
/// Wire shape of common-lib ShipmentDispatchedEvent. Timestamps are UTC DateTime so they
/// serialise with a trailing 'Z', which Jackson's Instant deserialiser accepts.
/// </summary>
public sealed record ShipmentDispatchedEvent(
    string EventId,
    string EventType,
    DateTime OccurredAt,
    long ShipmentId,
    long OrderId,
    long UserId,
    string Carrier,
    string TrackingNumber,
    DateTime ShippedAt,
    EventAddress ShippingAddress)
{
    public const string Type = "shipment.dispatched.v1";

    public static ShipmentDispatchedEvent From(Shipment shipment, DateTimeOffset occurredAt)
    {
        if (shipment.Carrier is null || shipment.TrackingNumber is null || shipment.ShippedAt is null)
        {
            throw new InvalidOperationException($"Shipment {shipment.Id} has not been shipped yet.");
        }

        return new ShipmentDispatchedEvent(
            EventIdFor(shipment.Id),
            Type,
            occurredAt.UtcDateTime,
            shipment.Id,
            shipment.OrderId,
            shipment.UserId,
            shipment.Carrier,
            shipment.TrackingNumber,
            shipment.ShippedAt.Value.UtcDateTime,
            EventAddress.From(shipment.ShippingAddress));
    }

    /// <summary>Deterministic per shipment, so a re-publish after a failure carries the same id.</summary>
    public static string EventIdFor(long shipmentId) =>
        new Guid(MD5.HashData(Encoding.UTF8.GetBytes($"{Type}:{shipmentId}"))).ToString();
}
