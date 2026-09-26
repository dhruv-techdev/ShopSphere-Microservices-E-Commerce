namespace ShippingService.Api.Shipping;

public enum HandleOutcome
{
    /// <summary>Shipment created, tracking number assigned, shipment.dispatched published.</summary>
    Created,
    /// <summary>Event (or order) already handled; any missing dispatch publish was retried.</summary>
    Duplicate,
    /// <summary>No usable shipping address (e.g. legacy order or manual /simulate call). Recorded, not retried.</summary>
    SkippedNoAddress
}
