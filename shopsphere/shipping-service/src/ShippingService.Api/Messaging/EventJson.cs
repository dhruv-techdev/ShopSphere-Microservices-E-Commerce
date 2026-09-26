using System.Text.Json;
using ShippingService.Api.Messaging.Events;

namespace ShippingService.Api.Messaging;

/// <summary>camelCase JSON, matching the Java services' Jackson output.</summary>
public static class EventJson
{
    public static readonly JsonSerializerOptions Options = new(JsonSerializerDefaults.Web);

    public static PaymentSuccessfulEvent DeserializePaymentSuccessful(string json) =>
        JsonSerializer.Deserialize<PaymentSuccessfulEvent>(json, Options)
        ?? throw new InvalidEventException("payment.successful payload was null");

    public static string Serialize<T>(T value) => JsonSerializer.Serialize(value, Options);
}
