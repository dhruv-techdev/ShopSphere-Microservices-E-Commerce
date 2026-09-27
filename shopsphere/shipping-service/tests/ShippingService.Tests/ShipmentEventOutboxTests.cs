using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging.Abstractions;
using ShippingService.Api.Data;
using ShippingService.Api.Domain;
using ShippingService.Api.Messaging.Events;
using ShippingService.Api.Shipping;

namespace ShippingService.Tests;

public sealed class ShipmentEventOutboxTests : IDisposable
{
    private readonly ShippingDbContext _db;
    private readonly FakeShipmentEventPublisher _publisher = new();
    private readonly ShipmentEventOutbox _outbox;
    private readonly ShipmentLifecycleService _lifecycle;

    public ShipmentEventOutboxTests()
    {
        _db = new ShippingDbContext(new DbContextOptionsBuilder<ShippingDbContext>()
            .UseInMemoryDatabase($"outbox-{Guid.NewGuid()}")
            .Options);
        _outbox = new ShipmentEventOutbox(_db, _publisher, TimeProvider.System, NullLogger<ShipmentEventOutbox>.Instance);
        _lifecycle = new ShipmentLifecycleService(_db, _outbox, TimeProvider.System, NullLogger<ShipmentLifecycleService>.Instance);
    }

    public void Dispose() => _db.Dispose();

    private async Task<Shipment> SeedShippedAsync(long orderId)
    {
        var shipment = Shipment.Create(orderId, 7,
            ShippingAddress.Create("Jane Doe", "123 King St W", "Toronto", "M5V 3L9", "CA"));
        shipment.MarkShipped("ShopSphere Express", $"SSXTEST{orderId}", DateTimeOffset.UtcNow);
        _db.Shipments.Add(shipment);
        await _db.SaveChangesAsync();
        return shipment;
    }

    [Fact]
    public async Task Relay_PublishesDispatchedThenDelivered_InOrder_ThenNothing()
    {
        var shipment = await SeedShippedAsync(100);
        shipment.MarkDelivered(DateTimeOffset.UtcNow);
        await _db.SaveChangesAsync();

        var first = await _outbox.RepublishPendingAsync(TimeSpan.Zero, 100, CancellationToken.None);

        Assert.Equal(1, first);
        Assert.Single(_publisher.Published);
        Assert.Single(_publisher.Delivered);
        Assert.NotNull(shipment.DispatchPublishedAt);
        Assert.NotNull(shipment.DeliveredPublishedAt);
        Assert.Equal(ShipmentDeliveredEvent.EventIdFor(shipment.Id), _publisher.Delivered[0].EventId);

        var second = await _outbox.RepublishPendingAsync(TimeSpan.Zero, 100, CancellationToken.None);

        Assert.Equal(0, second);
        Assert.Single(_publisher.Published);
        Assert.Single(_publisher.Delivered);
    }

    [Fact]
    public async Task Relay_FailedPublish_IsRetriedOnNextPass()
    {
        await SeedShippedAsync(101);
        _publisher.FailNext = true;

        var first = await _outbox.RepublishPendingAsync(TimeSpan.Zero, 100, CancellationToken.None);
        Assert.Equal(0, first);
        Assert.Empty(_publisher.Published);

        var second = await _outbox.RepublishPendingAsync(TimeSpan.Zero, 100, CancellationToken.None);
        Assert.Equal(1, second);
        Assert.Single(_publisher.Published);
    }

    [Fact]
    public async Task Relay_SkipsRecentlyUpdatedShipments()
    {
        await SeedShippedAsync(102);

        var published = await _outbox.RepublishPendingAsync(TimeSpan.FromMinutes(5), 100, CancellationToken.None);

        Assert.Equal(0, published);
        Assert.Empty(_publisher.Published);
    }

    [Fact]
    public async Task Deliver_PublishFails_StateStillCommitted_AndRelayCatchesUp()
    {
        var shipment = await SeedShippedAsync(103);
        await _outbox.PublishDispatchedAsync(shipment, CancellationToken.None);
        _publisher.FailNext = true;

        var result = await _lifecycle.DeliverAsync(shipment.Id, CancellationToken.None);

        Assert.Equal(TransitionStatus.Ok, result.Status);
        Assert.Equal(ShipmentStatus.Delivered, shipment.Status);
        Assert.Null(shipment.DeliveredPublishedAt);
        Assert.Empty(_publisher.Delivered);

        await _outbox.RepublishPendingAsync(TimeSpan.Zero, 100, CancellationToken.None);

        Assert.Single(_publisher.Delivered);
        Assert.NotNull(shipment.DeliveredPublishedAt);
    }
}
