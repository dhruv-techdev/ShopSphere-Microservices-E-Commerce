using ShippingService.Api.Messaging;
using ShippingService.Api.Messaging.Events;

namespace ShippingService.Tests;

public sealed class FixedTimeProvider(DateTimeOffset now) : TimeProvider
{
    public override DateTimeOffset GetUtcNow() => now;
}

public sealed class FakeShipmentEventPublisher : IShipmentEventPublisher
{
    /// <summary>Every publish attempt, including failed ones.</summary>
    public List<ShipmentDispatchedEvent> Attempts { get; } = [];

    /// <summary>Successfully "acknowledged" publishes.</summary>
    public List<ShipmentDispatchedEvent> Published { get; } = [];

    public bool FailNext { get; set; }

    public Task PublishDispatchedAsync(ShipmentDispatchedEvent dispatched, CancellationToken cancellationToken)
    {
        Attempts.Add(dispatched);
        if (FailNext)
        {
            FailNext = false;
            throw new InvalidOperationException("Simulated broker failure");
        }
        Published.Add(dispatched);
        return Task.CompletedTask;
    }
}
