using Microsoft.EntityFrameworkCore;
using ShippingService.Api.Data;
using ShippingService.Api.Domain;
using ShippingService.Api.Messaging;
using ShippingService.Api.Messaging.Events;

namespace ShippingService.Api.Shipping;

/// <summary>
/// Publishes shipment events and records that they went out (dispatch_published_at /
/// delivered_published_at). State changes are committed first; events follow. Anything
/// left unpublished is picked up by ShipmentOutboxRelay. Event ids are deterministic, so
/// the occasional duplicate publish is harmless for idempotent consumers.
/// </summary>
public sealed class ShipmentEventOutbox(
    ShippingDbContext db,
    IShipmentEventPublisher publisher,
    TimeProvider timeProvider,
    ILogger<ShipmentEventOutbox> logger)
{
    public async Task PublishDispatchedAsync(Shipment shipment, CancellationToken ct)
    {
        var now = timeProvider.GetUtcNow();
        await publisher.PublishDispatchedAsync(ShipmentDispatchedEvent.From(shipment, now), ct);
        shipment.MarkDispatchPublished(now);
        await db.SaveChangesAsync(ct);
    }

    public async Task PublishDeliveredAsync(Shipment shipment, CancellationToken ct)
    {
        var now = timeProvider.GetUtcNow();
        await publisher.PublishDeliveredAsync(ShipmentDeliveredEvent.From(shipment, now), ct);
        shipment.MarkDeliveredPublished(now);
        await db.SaveChangesAsync(ct);
    }

    /// <summary>Publishes whatever is outstanding for this shipment, in lifecycle order.</summary>
    /// <returns>false if a publish failed (the relay will retry later).</returns>
    public async Task<bool> TryPublishPendingAsync(Shipment shipment, CancellationToken ct)
    {
        try
        {
            if (NeedsDispatchPublish(shipment))
            {
                await PublishDispatchedAsync(shipment, ct);
            }
            if (NeedsDeliveredPublish(shipment))
            {
                await PublishDeliveredAsync(shipment, ct);
            }
            return true;
        }
        catch (Exception ex) when (ex is not OperationCanceledException)
        {
            logger.LogWarning(ex, "Publishing events for shipment {ShipmentId} failed; the outbox relay will retry",
                shipment.Id);
            return false;
        }
    }

    /// <summary>Re-publishes events for shipments untouched for at least <paramref name="minAge"/>.</summary>
    /// <returns>Number of shipments fully caught up.</returns>
    public async Task<int> RepublishPendingAsync(TimeSpan minAge, int batchSize, CancellationToken ct)
    {
        var cutoff = DateTimeOffset.UtcNow - minAge;

        var pending = await db.Shipments
            .Where(s => s.UpdatedAt <= cutoff &&
                        ((s.DispatchPublishedAt == null &&
                          (s.Status == ShipmentStatus.Shipped || s.Status == ShipmentStatus.Delivered)) ||
                         (s.DeliveredPublishedAt == null && s.Status == ShipmentStatus.Delivered)))
            .OrderBy(s => s.Id)
            .Take(batchSize)
            .ToListAsync(ct);

        var caughtUp = 0;
        foreach (var shipment in pending)
        {
            if (await TryPublishPendingAsync(shipment, ct))
            {
                caughtUp++;
            }
        }
        return caughtUp;
    }

    internal static bool NeedsDispatchPublish(Shipment s) =>
        s.DispatchPublishedAt is null && s.Status is ShipmentStatus.Shipped or ShipmentStatus.Delivered;

    internal static bool NeedsDeliveredPublish(Shipment s) =>
        s.DeliveredPublishedAt is null && s.Status == ShipmentStatus.Delivered;
}
