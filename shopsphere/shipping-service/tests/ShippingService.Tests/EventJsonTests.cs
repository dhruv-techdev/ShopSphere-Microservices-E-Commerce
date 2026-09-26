using System.Text.Json;
using ShippingService.Api.Messaging;
using ShippingService.Api.Messaging.Events;

namespace ShippingService.Tests;

public sealed class EventJsonTests
{
    [Fact]
    public void DeserializePaymentSuccessful_AcceptsJavaProducedPayload()
    {
        // Shape produced by Spring Kafka's JsonSerializer for common-lib PaymentSuccessfulEvent.
        const string json = """
            {
              "eventId": "5b6c3f7e-2c1a-4b7e-9a51-0f7a1f0b9c11",
              "eventType": "payment.successful.v1",
              "occurredAt": 1790424000.123456789,
              "orderId": 42,
              "userId": 7,
              "paymentReference": "pay-ref-1",
              "amount": 100.50,
              "items": [ { "productId": 10, "quantity": 2, "unitPrice": 50.25 } ],
              "shippingAddress": {
                "recipientName": "Jane Doe",
                "line1": "123 King St W",
                "city": "Toronto",
                "state": "ON",
                "postalCode": "M5V 3L9",
                "country": "CA"
              }
            }
            """;

        var evt = EventJson.DeserializePaymentSuccessful(json);

        Assert.Equal("5b6c3f7e-2c1a-4b7e-9a51-0f7a1f0b9c11", evt.EventId);
        Assert.Equal(42, evt.OrderId);
        Assert.Equal(7, evt.UserId);
        Assert.Equal(100.50m, evt.Amount);
        Assert.NotNull(evt.ShippingAddress);
        Assert.Equal("Toronto", evt.ShippingAddress!.City);
        Assert.Null(evt.ShippingAddress.Line2);
    }

    [Fact]
    public void DeserializePaymentSuccessful_MalformedJson_Throws()
    {
        Assert.Throws<JsonException>(() => EventJson.DeserializePaymentSuccessful("{ not json"));
    }

    [Fact]
    public void Serialize_ShipmentDispatched_IsCamelCaseWithUtcTimestamps()
    {
        var evt = new ShipmentDispatchedEvent(
            ShipmentDispatchedEvent.EventIdFor(5), ShipmentDispatchedEvent.Type,
            new DateTime(2026, 9, 26, 12, 0, 0, DateTimeKind.Utc),
            5, 42, 7, "ShopSphere Express", "SSX2609264K7QZ9M2PA",
            new DateTime(2026, 9, 26, 12, 0, 0, DateTimeKind.Utc),
            new EventAddress("Jane Doe", null, "123 King St W", null, "Toronto", "ON", "M5V 3L9", "CA"));

        using var json = JsonDocument.Parse(EventJson.Serialize(evt));
        var root = json.RootElement;

        Assert.Equal("shipment.dispatched.v1", root.GetProperty("eventType").GetString());
        Assert.Equal(42, root.GetProperty("orderId").GetInt64());
        Assert.Equal("SSX2609264K7QZ9M2PA", root.GetProperty("trackingNumber").GetString());
        Assert.EndsWith("Z", root.GetProperty("occurredAt").GetString());
        Assert.EndsWith("Z", root.GetProperty("shippedAt").GetString());
        Assert.Equal("CA", root.GetProperty("shippingAddress").GetProperty("country").GetString());
    }

    [Fact]
    public void EventIdFor_IsDeterministicPerShipment()
    {
        Assert.Equal(ShipmentDispatchedEvent.EventIdFor(5), ShipmentDispatchedEvent.EventIdFor(5));
        Assert.NotEqual(ShipmentDispatchedEvent.EventIdFor(5), ShipmentDispatchedEvent.EventIdFor(6));
    }
}
