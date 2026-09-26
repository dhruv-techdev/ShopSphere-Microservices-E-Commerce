namespace ShippingService.Api.Messaging;

/// <summary>A message that can never be processed (poison). The consumer logs and skips it.</summary>
public sealed class InvalidEventException(string message) : Exception(message);
