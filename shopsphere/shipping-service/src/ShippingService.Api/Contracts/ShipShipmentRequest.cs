using System.Text.RegularExpressions;

namespace ShippingService.Api.Contracts;

/// <summary>
/// US45 — body of POST /api/v1/shipments/{id}/ship. Both fields are optional:
/// a blank carrier uses Shipping:DefaultCarrier, a blank tracking number is generated.
/// </summary>
public sealed record ShipShipmentRequest(string? Carrier, string? TrackingNumber)
{
    public const int MaxLength = 100;

    private static readonly Regex TrackingNumberPattern = new("^[A-Za-z0-9-]{4,100}$", RegexOptions.Compiled);

    public Dictionary<string, string[]> Validate()
    {
        var errors = new Dictionary<string, string[]>();
        if (Carrier is not null && Carrier.Trim().Length > MaxLength)
        {
            errors["carrier"] = [$"Carrier must be at most {MaxLength} characters."];
        }
        if (!string.IsNullOrWhiteSpace(TrackingNumber) && !TrackingNumberPattern.IsMatch(TrackingNumber.Trim()))
        {
            errors["trackingNumber"] = ["Tracking number must be 4-100 letters, digits or dashes."];
        }
        return errors;
    }
}
