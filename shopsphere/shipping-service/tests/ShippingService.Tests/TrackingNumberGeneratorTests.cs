using ShippingService.Api.Options;
using ShippingService.Api.Tracking;
using MsOptions = Microsoft.Extensions.Options.Options;

namespace ShippingService.Tests;

public sealed class TrackingNumberGeneratorTests
{
    private readonly TrackingNumberGenerator _generator = new(
        MsOptions.Create(new ShippingOptions { TrackingPrefix = "SSX" }),
        new FixedTimeProvider(new DateTimeOffset(2026, 9, 26, 23, 59, 0, TimeSpan.Zero)));

    [Fact]
    public void Next_HasPrefixDateAndCrockfordSuffix()
    {
        Assert.Matches("^SSX260926[0-9A-HJKMNP-TV-Z]{10}$", _generator.Next());
    }

    [Fact]
    public void Next_IsUniqueAcrossManyCalls()
    {
        var numbers = Enumerable.Range(0, 10_000).Select(_ => _generator.Next()).ToHashSet();

        Assert.Equal(10_000, numbers.Count);
    }
}
