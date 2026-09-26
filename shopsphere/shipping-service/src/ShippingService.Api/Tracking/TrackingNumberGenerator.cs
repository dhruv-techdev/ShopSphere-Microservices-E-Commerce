using System.Security.Cryptography;
using Microsoft.Extensions.Options;
using ShippingService.Api.Options;

namespace ShippingService.Api.Tracking;

/// <summary>
/// Format: {PREFIX}{yyMMdd}{10 random Crockford base32 chars}, e.g. SSX2609264K7QZ9M2PA.
/// 32^10 ≈ 1.1e15 combinations per day; a unique index on tracking_number backs it up.
/// Crockford's alphabet drops I, L, O, U so numbers are easy to read out over the phone.
/// </summary>
public sealed class TrackingNumberGenerator(IOptions<ShippingOptions> options, TimeProvider timeProvider)
    : ITrackingNumberGenerator
{
    private const string Alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private const int RandomLength = 10;

    public string Next()
    {
        Span<char> random = stackalloc char[RandomLength];
        for (var i = 0; i < RandomLength; i++)
        {
            random[i] = Alphabet[RandomNumberGenerator.GetInt32(Alphabet.Length)];
        }

        var date = timeProvider.GetUtcNow().ToString("yyMMdd");
        return string.Concat(options.Value.TrackingPrefix, date, random);
    }
}
