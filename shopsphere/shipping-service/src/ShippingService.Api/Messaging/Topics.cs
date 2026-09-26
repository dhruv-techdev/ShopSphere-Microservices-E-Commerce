namespace ShippingService.Api.Messaging;

/// <summary>Mirrors com.shopsphere.common.events.Topics in common-lib.</summary>
public static class Topics
{
    public const string PaymentSuccessful = "payment.successful";
    public const string ShipmentDispatched = "shipment.dispatched";
}
