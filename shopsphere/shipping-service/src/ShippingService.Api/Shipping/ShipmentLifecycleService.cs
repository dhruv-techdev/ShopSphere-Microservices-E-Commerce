using Microsoft.EntityFrameworkCore;
using ShippingService.Api.Data;
using ShippingService.Api.Domain;

namespace ShippingService.Api.Shipping;

public enum TransitionStatus
{
    Ok,
    NotFound,
    Conflict
}

public sealed record TransitionResult(TransitionStatus Status, Shipment? Shipment, string? Error)
{
    public static TransitionResult Ok(Shipment s) => new(TransitionStatus.Ok, s, null);
    public static TransitionResult NotFound(long id) => new(TransitionStatus.NotFound, null, $"Shipment {id} not found");
    public static TransitionResult Conflict(Shipment s, string error) => new(TransitionStatus.Conflict, s, error);
}

/// <summary>
/// US36 — manual/carrier-driven status transitions. Both operations are idempotent:
/// repeating a transition that already happened returns 200 and re-tries any unpublished event.
/// The state change is committed before publishing; a failed publish is retried by the relay.
/// </summary>
public sealed class ShipmentLifecycleService(
    ShippingDbContext db,
    ShipmentEventOutbox outbox,
    TimeProvider timeProvider,
    ILogger<ShipmentLifecycleService> logger)
{
    public async Task<TransitionResult> DeliverAsync(long shipmentId, CancellationToken ct)
    {
        var shipment = await db.Shipments.FirstOrDefaultAsync(s => s.Id == shipmentId, ct);
        if (shipment is null)
        {
            return TransitionResult.NotFound(shipmentId);
        }

        if (shipment.Status == ShipmentStatus.Delivered)
        {
            await outbox.TryPublishPendingAsync(shipment, ct);
            return TransitionResult.Ok(shipment);
        }

        if (shipment.Status != ShipmentStatus.Shipped)
        {
            return TransitionResult.Conflict(shipment,
                $"Shipment {shipmentId} is {Name(shipment.Status)}; only SHIPPED shipments can be delivered.");
        }

        shipment.MarkDelivered(timeProvider.GetUtcNow());
        await db.SaveChangesAsync(ct);
        logger.LogInformation("Shipment {ShipmentId} for order {OrderId} marked DELIVERED", shipment.Id, shipment.OrderId);

        await outbox.TryPublishPendingAsync(shipment, ct);
        return TransitionResult.Ok(shipment);
    }

    public async Task<TransitionResult> CancelAsync(long shipmentId, CancellationToken ct)
    {
        var shipment = await db.Shipments.FirstOrDefaultAsync(s => s.Id == shipmentId, ct);
        if (shipment is null)
        {
            return TransitionResult.NotFound(shipmentId);
        }

        if (shipment.Status == ShipmentStatus.Cancelled)
        {
            return TransitionResult.Ok(shipment);
        }

        if (shipment.Status != ShipmentStatus.Pending)
        {
            return TransitionResult.Conflict(shipment,
                $"Shipment {shipmentId} is {Name(shipment.Status)}; only PENDING shipments can be cancelled.");
        }

        shipment.Cancel();
        await db.SaveChangesAsync(ct);
        logger.LogInformation("Shipment {ShipmentId} for order {OrderId} CANCELLED", shipment.Id, shipment.OrderId);
        return TransitionResult.Ok(shipment);
    }

    private static string Name(ShipmentStatus status) => status.ToString().ToUpperInvariant();
}
