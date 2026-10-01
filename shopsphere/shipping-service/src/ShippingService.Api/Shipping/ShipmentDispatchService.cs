using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;
using Npgsql;
using ShippingService.Api.Data;
using ShippingService.Api.Domain;
using ShippingService.Api.Options;
using ShippingService.Api.Tracking;

namespace ShippingService.Api.Shipping;

/// <summary>
/// US45 — admin-triggered dispatch: PENDING → SHIPPED with carrier + tracking number, then
/// shipment.dispatched (order-service moves the order to SHIPPED and the customer is emailed).
/// Idempotent: shipping an already SHIPPED/DELIVERED shipment returns it unchanged and only
/// re-tries any unpublished event. The state change is committed before publishing; a failed
/// publish is retried by ShipmentOutboxRelay.
/// </summary>
public sealed class ShipmentDispatchService(
    ShippingDbContext db,
    ShipmentEventOutbox outbox,
    ITrackingNumberGenerator trackingNumbers,
    IOptions<ShippingOptions> shippingOptions,
    TimeProvider timeProvider,
    ILogger<ShipmentDispatchService> logger)
{
    public async Task<TransitionResult> ShipAsync(long shipmentId, string? carrier, string? trackingNumber, CancellationToken ct)
    {
        var shipment = await db.Shipments.FirstOrDefaultAsync(s => s.Id == shipmentId, ct);
        if (shipment is null)
        {
            return TransitionResult.NotFound(shipmentId);
        }

        if (shipment.Status is ShipmentStatus.Shipped or ShipmentStatus.Delivered)
        {
            await outbox.TryPublishPendingAsync(shipment, ct);
            return TransitionResult.Ok(shipment);
        }

        if (shipment.Status != ShipmentStatus.Pending)
        {
            return TransitionResult.Conflict(shipment,
                $"Shipment {shipmentId} is {shipment.Status.ToString().ToUpperInvariant()}; only PENDING shipments can be shipped.");
        }

        var tracking = string.IsNullOrWhiteSpace(trackingNumber)
            ? trackingNumbers.Next()
            : trackingNumber.Trim().ToUpperInvariant();

        if (await db.Shipments.AnyAsync(s => s.TrackingNumber == tracking && s.Id != shipmentId, ct))
        {
            return TransitionResult.Conflict(shipment, $"Tracking number {tracking} is already used by another shipment.");
        }

        var resolvedCarrier = string.IsNullOrWhiteSpace(carrier) ? shippingOptions.Value.DefaultCarrier : carrier.Trim();
        shipment.MarkShipped(resolvedCarrier, tracking, timeProvider.GetUtcNow());

        try
        {
            await db.SaveChangesAsync(ct);
        }
        catch (DbUpdateException ex) when (ex.InnerException is PostgresException { SqlState: PostgresErrorCodes.UniqueViolation })
        {
            db.ChangeTracker.Clear();
            return TransitionResult.Conflict(shipment, $"Tracking number {tracking} is already used by another shipment.");
        }

        logger.LogInformation("Shipment {ShipmentId} for order {OrderId} SHIPPED by admin carrier={Carrier} tracking={TrackingNumber}",
            shipment.Id, shipment.OrderId, shipment.Carrier, shipment.TrackingNumber);

        await outbox.TryPublishPendingAsync(shipment, ct);
        return TransitionResult.Ok(shipment);
    }
}
