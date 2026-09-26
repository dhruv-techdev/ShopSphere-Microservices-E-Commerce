using System.ComponentModel.DataAnnotations;

namespace ShippingService.Api.Options;

public sealed class ShippingOptions
{
    public const string SectionName = "Shipping";

    [Required, MaxLength(100)]
    public string DefaultCarrier { get; set; } = "ShopSphere Express";

    /// <summary>2–5 upper-case letters prefixed to every tracking number.</summary>
    [Required, RegularExpression("^[A-Z]{2,5}$")]
    public string TrackingPrefix { get; set; } = "SSX";
}
