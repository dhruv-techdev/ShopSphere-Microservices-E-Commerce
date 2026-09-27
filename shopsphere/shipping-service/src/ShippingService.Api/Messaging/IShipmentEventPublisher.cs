using ShippingService.Api.Messaging.Events;

namespace ShippingService.Api.Messaging;

public interface IShipmentEventPublisher
{
    /// <summary>Completes only after the broker acknowledges the write (acks=all).</summary>
    Task PublishDispatchedAsync(ShipmentDispatchedEvent dispatched, CancellationToken cancellationToken);

    /// <summary>Completes only after the broker acknowledges the write (acks=all).</summary>
    Task PublishDeliveredAsync(ShipmentDeliveredEvent delivered, CancellationToken cancellationToken);
}
