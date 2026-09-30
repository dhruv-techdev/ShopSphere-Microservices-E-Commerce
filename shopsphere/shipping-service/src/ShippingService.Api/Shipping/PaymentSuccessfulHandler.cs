using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;
using Npgsql;
using ShippingService.Api.Data;
using ShippingService.Api.Domain;
using ShippingService.Api.Messaging;
using ShippingService.Api.Messaging.Events;
using ShippingService.Api.Options;
using ShippingService.Api.Tracking;

namespace ShippingService.Api.Shipping;

/// <summary>
/// payment.successful → shipment (SHIPPED, with tracking number) → shipment.dispatched.
///
/// Idempotency:
///  1. processed_events has one row per consumed eventId, saved in the SAME SaveChanges
///     as the shipment, so they commit or roll back together.
///  2. shipments.order_id is unique, so a different event for the same order can't create a
///     second shipment.
///  3. dispatch_published_at records a successful publish. If the publish fails, the handler
///     throws, the offset isn't committed, and the redelivery re-publishes (same eventId).
/// </summary>
public sealed class PaymentSuccessfulHandler(
    ShippingDbContext db,
    ITrackingNumberGenerator trackingNumbers,
    ShipmentEventOutbox outbox,
    IOptions<ShippingOptions> shippingOptions,
    TimeProvider timeProvider,
    ILogger<PaymentSuccessfulHandler> logger)
{
    public async Task<HandleOutcome> HandleAsync(PaymentSuccessfulEvent evt, CancellationToken ct)
    {
        Validate(evt);
        var eventId = evt.EventId!;

        // 1. Same event seen before → just make sure its dispatch went out.
        if (await db.ProcessedEvents.AsNoTracking().AnyAsync(p => p.EventId == eventId, ct))
        {
            logger.LogInformation("Duplicate payment.successful eventId={EventId} orderId={OrderId}", eventId, evt.OrderId);
            await EnsureDispatchPublishedAsync(evt.OrderId, ct);
            return HandleOutcome.Duplicate;
        }

        var now = timeProvider.GetUtcNow();
        var processed = ProcessedEvent.Create(eventId, evt.EventType ?? PaymentSuccessfulEvent.Type, now);

        // 2. Different event, same order → never ship twice.
        if (await db.Shipments.AnyAsync(s => s.OrderId == evt.OrderId, ct))
        {
            logger.LogWarning("Shipment already exists for orderId={OrderId}; recording eventId={EventId} as processed",
                evt.OrderId, eventId);
            db.ProcessedEvents.Add(processed);
            await SaveIgnoringDuplicatesAsync(ct);
            await EnsureDispatchPublishedAsync(evt.OrderId, ct);
            return HandleOutcome.Duplicate;
        }

        // 3. No usable address → record and skip (retrying won't help).
        var address = TryMapAddress(evt.ShippingAddress, out var reason);
        if (address is null)
        {
            logger.LogWarning("Skipping payment.successful eventId={EventId} orderId={OrderId}: {Reason}",
                eventId, evt.OrderId, reason);
            db.ProcessedEvents.Add(processed);
            await SaveIgnoringDuplicatesAsync(ct);
            return HandleOutcome.SkippedNoAddress;
        }

        // 4. Create + dispatch.
        var shipment = Shipment.Create(evt.OrderId, evt.UserId, address);
        shipment.MarkShipped(shippingOptions.Value.DefaultCarrier, trackingNumbers.Next(), now);

        db.Shipments.Add(shipment);
        db.ProcessedEvents.Add(processed);

        if (!await SaveIgnoringDuplicatesAsync(ct))
        {
            // Lost a race with a concurrent delivery of the same event/order.
            await EnsureDispatchPublishedAsync(evt.OrderId, ct);
            return HandleOutcome.Duplicate;
        }

        logger.LogInformation("Created shipment {ShipmentId} for orderId={OrderId} carrier={Carrier} tracking={TrackingNumber}",
            shipment.Id, shipment.OrderId, shipment.Carrier, shipment.TrackingNumber);

        await outbox.PublishDispatchedAsync(shipment, ct);
        return HandleOutcome.Created;
    }

    private static void Validate(PaymentSuccessfulEvent evt)
    {
        if (string.IsNullOrWhiteSpace(evt.EventId))
        {
            throw new InvalidEventException("payment.successful is missing eventId");
        }
        if (evt.EventId.Length > ProcessedEvent.EventIdMaxLength)
        {
            throw new InvalidEventException($"payment.successful eventId longer than {ProcessedEvent.EventIdMaxLength} chars");
        }
        if (evt.OrderId <= 0)
        {
            throw new InvalidEventException($"payment.successful eventId={evt.EventId} has invalid orderId {evt.OrderId}");
        }
        if (evt.UserId <= 0)
        {
            throw new InvalidEventException($"payment.successful eventId={evt.EventId} has invalid userId {evt.UserId}");
        }
    }

    private static ShippingAddress? TryMapAddress(EventAddress? a, out string reason)
    {
        if (a is null)
        {
            reason = "no shippingAddress on event";
            return null;
        }
        try
        {
            reason = string.Empty;
            return ShippingAddress.Create(
                a.RecipientName!, a.Line1!, a.City!, a.PostalCode!, a.Country!,
                line2: a.Line2, state: a.State, phone: a.Phone);
        }
        catch (ArgumentException ex)
        {
            reason = $"invalid shippingAddress: {ex.Message}";
            return null;
        }
    }

    private async Task EnsureDispatchPublishedAsync(long orderId, CancellationToken ct)
    {
        var shipment = await db.Shipments.FirstOrDefaultAsync(s => s.OrderId == orderId, ct);
        if (shipment is not null && ShipmentEventOutbox.NeedsDispatchPublish(shipment))
        {
            logger.LogInformation("Re-publishing shipment.dispatched for shipment {ShipmentId}", shipment.Id);
            await outbox.PublishDispatchedAsync(shipment, ct);
        }
    }

    /// <returns>false if a unique constraint (event id / order id) was hit — someone else won.</returns>
    private async Task<bool> SaveIgnoringDuplicatesAsync(CancellationToken ct)
    {
        try
        {
            await db.SaveChangesAsync(ct);
            return true;
        }
        catch (DbUpdateException ex) when (ex.InnerException is PostgresException { SqlState: PostgresErrorCodes.UniqueViolation })
        {
            db.ChangeTracker.Clear();
            return false;
        }
    }
}
