using ShippingService.Api.Messaging;
using ShippingService.Api.Messaging.Events;

namespace ShippingService.Tests;

public sealed class FixedTimeProvider(DateTimeOffset now) : TimeProvider
{
    public override DateTimeOffset GetUtcNow() => now;
}

public sealed class FakeShipmentEventPublisher : IShipmentEventPublisher
{
    private readonly object _gate = new();

    /// <summary>Every dispatched publish attempt, including failed ones.</summary>
    public List<ShipmentDispatchedEvent> Attempts { get; } = [];

    /// <summary>Successfully "acknowledged" dispatched publishes.</summary>
    public List<ShipmentDispatchedEvent> Published { get; } = [];

    /// <summary>Successfully "acknowledged" delivered publishes.</summary>
    public List<ShipmentDeliveredEvent> Delivered { get; } = [];

    /// <summary>Fail the next publish (of any type) once.</summary>
    public bool FailNext { get; set; }

    public Task PublishDispatchedAsync(ShipmentDispatchedEvent dispatched, CancellationToken cancellationToken)
    {
        lock (_gate)
        {
            Attempts.Add(dispatched);
            ThrowIfFailing();
            Published.Add(dispatched);
        }
        return Task.CompletedTask;
    }

    public Task PublishDeliveredAsync(ShipmentDeliveredEvent delivered, CancellationToken cancellationToken)
    {
        lock (_gate)
        {
            ThrowIfFailing();
            Delivered.Add(delivered);
        }
        return Task.CompletedTask;
    }

    private void ThrowIfFailing()
    {
        if (FailNext)
        {
            FailNext = false;
            throw new InvalidOperationException("Simulated broker failure");
        }
    }
}
