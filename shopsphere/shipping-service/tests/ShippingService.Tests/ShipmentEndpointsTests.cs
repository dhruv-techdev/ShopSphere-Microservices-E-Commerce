using System.Net;
using System.Text.Json;
using ShippingService.Api.Domain;

namespace ShippingService.Tests;

public sealed class ShipmentEndpointsTests(ShippingApiFactory factory) : IClassFixture<ShippingApiFactory>
{
    private static Shipment NewShipment(long orderId, long userId) =>
        Shipment.Create(orderId, userId,
            ShippingAddress.Create("Jane Doe", "123 King St W", "Toronto", "m5v 3l9", "ca", state: "ON"));

    private HttpClient ClientFor(long userId)
    {
        var client = factory.CreateClient();
        client.DefaultRequestHeaders.Add("X-User-Id", userId.ToString());
        return client;
    }

    [Fact]
    public async Task GetByOrderId_Owner_ReturnsShipment()
    {
        await factory.SeedAsync(NewShipment(orderId: 1001, userId: 7));

        var response = await ClientFor(7).GetAsync("/api/v1/shipments/order/1001");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        using var json = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
        var root = json.RootElement;
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
}
