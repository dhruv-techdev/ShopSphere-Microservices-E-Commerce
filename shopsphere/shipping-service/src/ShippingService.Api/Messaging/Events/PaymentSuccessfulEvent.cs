namespace ShippingService.Api.Messaging.Events;

/// <summary>
/// Consumer-side view of common-lib PaymentSuccessfulEvent. Only the fields shipping needs;
/// everything else (items, occurredAt, ...) is ignored on deserialisation.
/// </summary>
public sealed record PaymentSuccessfulEvent(
    string? EventId,
    string? EventType,
    long OrderId,
    long UserId,
    string? PaymentReference,
    decimal? Amount,
    EventAddress? ShippingAddress)
{
    public const string Type = "payment.successful.v1";
}
