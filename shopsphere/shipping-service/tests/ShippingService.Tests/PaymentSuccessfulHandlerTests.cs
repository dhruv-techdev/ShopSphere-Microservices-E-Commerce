using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging.Abstractions;
using ShippingService.Api.Data;
using ShippingService.Api.Domain;
using ShippingService.Api.Messaging;
using ShippingService.Api.Messaging.Events;
using ShippingService.Api.Options;
using ShippingService.Api.Shipping;
using ShippingService.Api.Tracking;
using MsOptions = Microsoft.Extensions.Options.Options;

namespace ShippingService.Tests;

public sealed class PaymentSuccessfulHandlerTests : IDisposable
{
    private static readonly DateTimeOffset Now = new(2026, 9, 26, 12, 0, 0, TimeSpan.Zero);

    private readonly ShippingDbContext _db;
    private readonly FakeShipmentEventPublisher _publisher = new();
    private readonly PaymentSuccessfulHandler _handler;

    public PaymentSuccessfulHandlerTests()
    {
        var dbOptions = new DbContextOptionsBuilder<ShippingDbContext>()
            .UseInMemoryDatabase($"handler-{Guid.NewGuid()}")
            .Options;
        _db = new ShippingDbContext(dbOptions);

        var shippingOptions = MsOptions.Create(new ShippingOptions { DefaultCarrier = "ShopSphere Express", TrackingPrefix = "SSX" });
        var time = new FixedTimeProvider(Now);

        _handler = new PaymentSuccessfulHandler(
            _db,
            new TrackingNumberGenerator(shippingOptions, time),
            _publisher,
            shippingOptions,
            time,
            NullLogger<PaymentSuccessfulHandler>.Instance);
    }

    public void Dispose() => _db.Dispose();

    private static EventAddress Address(string country = "CA") =>
        new("Jane Doe", "+1 416 555 0199", "123 King St W", null, "Toronto", "ON", "M5V 3L9", country);

    private static PaymentSuccessfulEvent Event(string eventId = "evt-1", long orderId = 42, long userId = 7, EventAddress? address = null) =>
        new(eventId, PaymentSuccessfulEvent.Type, orderId, userId, "pay-ref", 100.00m, address ?? Address());

    [Fact]
    public async Task NewEvent_CreatesShippedShipmentWithTracking_AndPublishesDispatched()
    {
        var outcome = await _handler.HandleAsync(Event(), CancellationToken.None);

        Assert.Equal(HandleOutcome.Created, outcome);

        var shipment = await _db.Shipments.SingleAsync();
        Assert.Equal(42, shipment.OrderId);
        Assert.Equal(7, shipment.UserId);
        Assert.Equal(ShipmentStatus.Shipped, shipment.Status);
        Assert.Equal("ShopSphere Express", shipment.Carrier);
        Assert.Matches("^SSX260926[0-9A-HJKMNP-TV-Z]{10}$", shipment.TrackingNumber);
        Assert.Equal(Now, shipment.ShippedAt);
        Assert.Equal(Now, shipment.DispatchPublishedAt);
        Assert.Equal("Toronto", shipment.ShippingAddress.City);

        Assert.True(await _db.ProcessedEvents.AnyAsync(p => p.EventId == "evt-1"));

        var dispatched = Assert.Single(_publisher.Published);
        Assert.Equal(ShipmentDispatchedEvent.Type, dispatched.EventType);
        Assert.Equal(shipment.Id, dispatched.ShipmentId);
        Assert.Equal(42, dispatched.OrderId);
        Assert.Equal(shipment.TrackingNumber, dispatched.TrackingNumber);
        Assert.Equal("CA", dispatched.ShippingAddress.Country);
        Assert.Equal(DateTimeKind.Utc, dispatched.ShippedAt.Kind);
    }

    [Fact]
    public async Task SameEventTwice_CreatesOneShipment_AndPublishesOnce()
    {
        await _handler.HandleAsync(Event(), CancellationToken.None);
        var second = await _handler.HandleAsync(Event(), CancellationToken.None);

        Assert.Equal(HandleOutcome.Duplicate, second);
        Assert.Equal(1, await _db.Shipments.CountAsync());
        Assert.Single(_publisher.Published);
    }

    [Fact]
    public async Task DifferentEventForSameOrder_DoesNotCreateSecondShipment()
    {
        await _handler.HandleAsync(Event(eventId: "evt-1"), CancellationToken.None);
        var second = await _handler.HandleAsync(Event(eventId: "evt-2"), CancellationToken.None);

        Assert.Equal(HandleOutcome.Duplicate, second);
        Assert.Equal(1, await _db.Shipments.CountAsync());
        Assert.Equal(2, await _db.ProcessedEvents.CountAsync());
        Assert.Single(_publisher.Published);
    }

    [Fact]
    public async Task PublishFails_ThenRedelivered_RepublishesWithSameEventId()
    {
        _publisher.FailNext = true;

        await Assert.ThrowsAsync<InvalidOperationException>(() => _handler.HandleAsync(Event(), CancellationToken.None));

        var shipment = await _db.Shipments.SingleAsync();
        Assert.Null(shipment.DispatchPublishedAt);
        Assert.Empty(_publisher.Published);

        // Offset was not committed → Kafka redelivers the same event.
        var outcome = await _handler.HandleAsync(Event(), CancellationToken.None);

        Assert.Equal(HandleOutcome.Duplicate, outcome);
        Assert.Equal(1, await _db.Shipments.CountAsync());
        Assert.Single(_publisher.Published);
        Assert.Equal(2, _publisher.Attempts.Count);
        Assert.Equal(_publisher.Attempts[0].EventId, _publisher.Attempts[1].EventId);
        Assert.NotNull((await _db.Shipments.SingleAsync()).DispatchPublishedAt);
    }

    [Fact]
    public async Task MissingAddress_SkipsAndRecordsEvent()
    {
        var evt = new PaymentSuccessfulEvent("evt-no-addr", PaymentSuccessfulEvent.Type, 43, 7, "ref", 10m, null);

        var outcome = await _handler.HandleAsync(evt, CancellationToken.None);

        Assert.Equal(HandleOutcome.SkippedNoAddress, outcome);
        Assert.Empty(await _db.Shipments.ToListAsync());
        Assert.True(await _db.ProcessedEvents.AnyAsync(p => p.EventId == "evt-no-addr"));
        Assert.Empty(_publisher.Attempts);
    }

    [Fact]
    public async Task InvalidAddress_SkipsAndRecordsEvent()
    {
        var outcome = await _handler.HandleAsync(Event(eventId: "evt-bad-addr", address: Address(country: "CAN")), CancellationToken.None);

        Assert.Equal(HandleOutcome.SkippedNoAddress, outcome);
        Assert.Empty(await _db.Shipments.ToListAsync());
        Assert.Empty(_publisher.Attempts);
    }

    [Theory]
    [InlineData("", 42, 7)]
    [InlineData("evt-1", 0, 7)]
    [InlineData("evt-1", 42, 0)]
    public async Task InvalidEvent_ThrowsInvalidEventException(string eventId, long orderId, long userId)
    {
        var evt = new PaymentSuccessfulEvent(eventId, PaymentSuccessfulEvent.Type, orderId, userId, "ref", 10m, Address());

        await Assert.ThrowsAsync<InvalidEventException>(() => _handler.HandleAsync(evt, CancellationToken.None));
        Assert.Empty(await _db.Shipments.ToListAsync());
    }
}
