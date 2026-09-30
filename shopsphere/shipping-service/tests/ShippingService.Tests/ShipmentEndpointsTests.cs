using System.Net;
using System.Text.Json;
using Microsoft.Extensions.DependencyInjection;
using ShippingService.Api.Domain;
using ShippingService.Api.Messaging;

namespace ShippingService.Tests;

public sealed class ShipmentEndpointsTests(ShippingApiFactory factory) : IClassFixture<ShippingApiFactory>
{
    private static Shipment NewShipment(long orderId, long userId, bool shipped = false)
    {
        var shipment = Shipment.Create(orderId, userId,
            ShippingAddress.Create("Jane Doe", "123 King St W", "Toronto", "m5v 3l9", "ca", state: "ON"));
        if (shipped)
        {
            shipment.MarkShipped("ShopSphere Express", $"SSXE2E{orderId}", DateTimeOffset.UtcNow);
        }
        return shipment;
    }

    private HttpClient ClientFor(long userId, string role = "CUSTOMER")
    {
        var client = factory.CreateClient();
        client.DefaultRequestHeaders.Add("X-User-Id", userId.ToString());
        client.DefaultRequestHeaders.Add("X-User-Role", role);
        return client;
    }

    private HttpClient Admin() => ClientFor(1, "ADMIN");

    private FakeShipmentEventPublisher Publisher =>
        (FakeShipmentEventPublisher)factory.Services.GetRequiredService<IShipmentEventPublisher>();

    private static async Task<JsonElement> JsonAsync(HttpResponseMessage response)
    {
        using var doc = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
        return doc.RootElement.Clone();
    }

    /* ------------------------------ reads ------------------------------ */

    [Fact]
    public async Task GetByOrderId_Owner_ReturnsShipment()
    {
        await factory.SeedAsync(NewShipment(orderId: 1001, userId: 7));

        var response = await ClientFor(7).GetAsync("/api/v1/shipments/order/1001");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var root = await JsonAsync(response);
        Assert.Equal(1001, root.GetProperty("orderId").GetInt64());
        Assert.Equal("PENDING", root.GetProperty("status").GetString());
        Assert.Equal("CA", root.GetProperty("shippingAddress").GetProperty("country").GetString());
        Assert.Equal("M5V 3L9", root.GetProperty("shippingAddress").GetProperty("postalCode").GetString());
    }

    [Fact]
    public async Task GetById_Owner_ReturnsShipment()
    {
        var seeded = await factory.SeedAsync(NewShipment(orderId: 1002, userId: 7));

        var response = await ClientFor(7).GetAsync($"/api/v1/shipments/{seeded.Id}");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
    }

    [Fact]
    public async Task GetByOrderId_OtherUser_Returns403()
    {
        await factory.SeedAsync(NewShipment(orderId: 1003, userId: 7));

        var response = await ClientFor(99).GetAsync("/api/v1/shipments/order/1003");

        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
    }

    [Fact]
    public async Task GetByOrderId_Admin_CanReadAnyShipment()
    {
        await factory.SeedAsync(NewShipment(orderId: 1004, userId: 7));

        var response = await Admin().GetAsync("/api/v1/shipments/order/1004");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
    }

    [Fact]
    public async Task GetByOrderId_Unknown_Returns404()
    {
        var response = await ClientFor(7).GetAsync("/api/v1/shipments/order/999999");

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
    }

    [Fact]
    public async Task GetByOrderId_MissingUserHeader_Returns400()
    {
        var response = await factory.CreateClient().GetAsync("/api/v1/shipments/order/1001");

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
    }

    /* --------------------------- transitions --------------------------- */

    [Fact]
    public async Task Deliver_Admin_MarksDelivered_AndPublishesShipmentDelivered()
    {
        var seeded = await factory.SeedAsync(NewShipment(orderId: 2001, userId: 7, shipped: true));

        var response = await Admin().PostAsync($"/api/v1/shipments/{seeded.Id}/deliver", null);

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var root = await JsonAsync(response);
        Assert.Equal("DELIVERED", root.GetProperty("status").GetString());
        Assert.Equal(JsonValueKind.String, root.GetProperty("deliveredAt").ValueKind);

        var delivered = Assert.Single(Publisher.Delivered, e => e.ShipmentId == seeded.Id);
        Assert.Equal(2001, delivered.OrderId);
        Assert.Equal("shipment.delivered.v1", delivered.EventType);
    }

    [Fact]
    public async Task Deliver_Twice_IsIdempotent_AndPublishesOnce()
    {
        var seeded = await factory.SeedAsync(NewShipment(orderId: 2002, userId: 7, shipped: true));

        var first = await Admin().PostAsync($"/api/v1/shipments/{seeded.Id}/deliver", null);
        var second = await Admin().PostAsync($"/api/v1/shipments/{seeded.Id}/deliver", null);

        Assert.Equal(HttpStatusCode.OK, first.StatusCode);
        Assert.Equal(HttpStatusCode.OK, second.StatusCode);
        Assert.Single(Publisher.Delivered, e => e.ShipmentId == seeded.Id);
    }

    [Fact]
    public async Task Deliver_NonAdmin_Returns403()
    {
        var seeded = await factory.SeedAsync(NewShipment(orderId: 2003, userId: 7, shipped: true));

        var response = await ClientFor(7).PostAsync($"/api/v1/shipments/{seeded.Id}/deliver", null);

        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
    }

    [Fact]
    public async Task Deliver_PendingShipment_Returns409()
    {
        var seeded = await factory.SeedAsync(NewShipment(orderId: 2004, userId: 7));

        var response = await Admin().PostAsync($"/api/v1/shipments/{seeded.Id}/deliver", null);

        Assert.Equal(HttpStatusCode.Conflict, response.StatusCode);
    }

    [Fact]
    public async Task Deliver_Unknown_Returns404()
    {
        var response = await Admin().PostAsync("/api/v1/shipments/987654/deliver", null);

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
    }

    [Fact]
    public async Task Cancel_PendingShipment_Cancels()
    {
        var seeded = await factory.SeedAsync(NewShipment(orderId: 2005, userId: 7));

        var response = await Admin().PostAsync($"/api/v1/shipments/{seeded.Id}/cancel", null);

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal("CANCELLED", (await JsonAsync(response)).GetProperty("status").GetString());
    }

    [Fact]
    public async Task Cancel_ShippedShipment_Returns409()
    {
        var seeded = await factory.SeedAsync(NewShipment(orderId: 2006, userId: 7, shipped: true));

        var response = await Admin().PostAsync($"/api/v1/shipments/{seeded.Id}/cancel", null);

        Assert.Equal(HttpStatusCode.Conflict, response.StatusCode);
    }
}
