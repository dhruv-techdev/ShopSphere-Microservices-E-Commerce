using ShippingService.Api.Domain;

namespace ShippingService.Tests;

public sealed class ShipmentTests
{
    private static ShippingAddress ValidAddress() =>
        ShippingAddress.Create(" Jane Doe ", "123 King St W", "Toronto", "m5v 3l9", "ca", line2: "  ", state: "ON");

    [Fact]
    public void Create_StartsPending_WithNormalisedAddress()
    {
        var shipment = Shipment.Create(1, 2, ValidAddress());

        Assert.Equal(ShipmentStatus.Pending, shipment.Status);
        Assert.Equal("Jane Doe", shipment.ShippingAddress.RecipientName);
        Assert.Null(shipment.ShippingAddress.Line2);
        Assert.Equal("M5V 3L9", shipment.ShippingAddress.PostalCode);
        Assert.Equal("CA", shipment.ShippingAddress.Country);
    }

    [Theory]
    [InlineData(0, 1)]
    [InlineData(1, 0)]
    [InlineData(-1, 1)]
    public void Create_InvalidIds_Throws(long orderId, long userId)
    {
        Assert.Throws<ArgumentOutOfRangeException>(() => Shipment.Create(orderId, userId, ValidAddress()));
    }

    [Fact]
    public void Create_NullAddress_Throws()
    {
        Assert.Throws<ArgumentNullException>(() => Shipment.Create(1, 1, null!));
    }

    [Fact]
    public void HappyPath_PendingToShippedToDelivered()
    {
        var shipment = Shipment.Create(1, 2, ValidAddress());
        var shippedAt = DateTimeOffset.UtcNow;

        shipment.MarkShipped(" ShopSphere Express ", " TRK123 ", shippedAt);
        Assert.Equal(ShipmentStatus.Shipped, shipment.Status);
        Assert.Equal("ShopSphere Express", shipment.Carrier);
        Assert.Equal("TRK123", shipment.TrackingNumber);
        Assert.Equal(shippedAt, shipment.ShippedAt);

        shipment.MarkDelivered(shippedAt.AddDays(2));
        Assert.Equal(ShipmentStatus.Delivered, shipment.Status);
        Assert.NotNull(shipment.DeliveredAt);
    }

    [Fact]
    public void MarkDelivered_WhenPending_Throws()
    {
        var shipment = Shipment.Create(1, 2, ValidAddress());

        Assert.Throws<InvalidOperationException>(() => shipment.MarkDelivered(DateTimeOffset.UtcNow));
    }

    [Fact]
    public void Cancel_AfterShipping_Throws()
    {
        var shipment = Shipment.Create(1, 2, ValidAddress());
        shipment.MarkShipped("Carrier", "TRK", DateTimeOffset.UtcNow);

        Assert.Throws<InvalidOperationException>(() => shipment.Cancel());
    }

    [Fact]
    public void Cancel_WhenPending_Cancels()
    {
        var shipment = Shipment.Create(1, 2, ValidAddress());

        shipment.Cancel();

        Assert.Equal(ShipmentStatus.Cancelled, shipment.Status);
    }

    [Theory]
    [InlineData("CAN")]
    [InlineData("C")]
    [InlineData("1A")]
    public void Address_InvalidCountry_Throws(string country)
    {
        Assert.Throws<ArgumentException>(() =>
            ShippingAddress.Create("Jane", "Line 1", "Toronto", "M5V3L9", country));
    }

    [Fact]
    public void Address_MissingRequiredField_Throws()
    {
        Assert.Throws<ArgumentException>(() =>
            ShippingAddress.Create("Jane", "   ", "Toronto", "M5V3L9", "CA"));
    }
}
