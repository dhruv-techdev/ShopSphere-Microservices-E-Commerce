using System.Text.RegularExpressions;

namespace ShippingService.Api.Domain;

/// <summary>
/// Owned value object. Mirrors the order-service Address (US33) so the order.created
/// payload maps across 1:1.
/// </summary>
public sealed class ShippingAddress
{
    public const int RecipientNameMaxLength = 100;
    public const int PhoneMaxLength = 20;
    public const int LineMaxLength = 200;
    public const int CityMaxLength = 100;
    public const int StateMaxLength = 100;
    public const int PostalCodeMaxLength = 20;
    public const int CountryLength = 2;

    private static readonly Regex CountryPattern = new("^[A-Z]{2}$", RegexOptions.Compiled);

    private ShippingAddress() { } // EF Core

    public string RecipientName { get; private set; } = null!;
    public string? Phone { get; private set; }
    public string Line1 { get; private set; } = null!;
    public string? Line2 { get; private set; }
    public string City { get; private set; } = null!;
    public string? State { get; private set; }
    public string PostalCode { get; private set; } = null!;
    /// <summary>ISO 3166-1 alpha-2, upper case.</summary>
    public string Country { get; private set; } = null!;

    public static ShippingAddress Create(
        string recipientName,
        string line1,
        string city,
        string postalCode,
        string country,
        string? line2 = null,
        string? state = null,
        string? phone = null)
    {
        var normalisedCountry = Required(country, nameof(country), CountryLength).ToUpperInvariant();
        if (!CountryPattern.IsMatch(normalisedCountry))
        {
            throw new ArgumentException("country must be a 2-letter ISO 3166-1 alpha-2 code", nameof(country));
        }

        return new ShippingAddress
        {
            RecipientName = Required(recipientName, nameof(recipientName), RecipientNameMaxLength),
            Phone = Optional(phone, nameof(phone), PhoneMaxLength),
            Line1 = Required(line1, nameof(line1), LineMaxLength),
            Line2 = Optional(line2, nameof(line2), LineMaxLength),
            City = Required(city, nameof(city), CityMaxLength),
            State = Optional(state, nameof(state), StateMaxLength),
            PostalCode = Required(postalCode, nameof(postalCode), PostalCodeMaxLength).ToUpperInvariant(),
            Country = normalisedCountry
        };
    }

    private static string Required(string? value, string name, int maxLength)
    {
        var trimmed = value?.Trim();
        if (string.IsNullOrEmpty(trimmed))
        {
            throw new ArgumentException($"{name} is required", name);
        }
        if (trimmed.Length > maxLength)
        {
            throw new ArgumentException($"{name} must be at most {maxLength} characters", name);
        }
        return trimmed;
    }

    private static string? Optional(string? value, string name, int maxLength)
    {
        var trimmed = value?.Trim();
        if (string.IsNullOrEmpty(trimmed))
        {
            return null;
        }
        if (trimmed.Length > maxLength)
        {
            throw new ArgumentException($"{name} must be at most {maxLength} characters", name);
        }
        return trimmed;
    }
}
