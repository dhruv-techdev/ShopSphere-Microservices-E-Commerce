using System.Net;
using System.Text.Json;

namespace ShippingService.Tests;

public sealed class HealthEndpointTests(ShippingApiFactory factory) : IClassFixture<ShippingApiFactory>
{
    [Theory]
    [InlineData("/health")]
    [InlineData("/health/live")]
    [InlineData("/api/v1/shipments/health")]
    public async Task HealthEndpoints_ReturnUp(string path)
    {
        var client = factory.CreateClient();

        var response = await client.GetAsync(path);

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        using var json = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
        Assert.Equal("UP", json.RootElement.GetProperty("status").GetString());
        Assert.Equal("shipping-service", json.RootElement.GetProperty("service").GetString());
    }

    [Fact]
    public async Task Health_IncludesDatabaseCheck()
    {
        var client = factory.CreateClient();

        using var json = JsonDocument.Parse(await client.GetStringAsync("/health"));
        var checks = json.RootElement.GetProperty("checks").EnumerateArray()
            .Select(c => c.GetProperty("name").GetString())
            .ToList();

        Assert.Contains("self", checks);
        Assert.Contains("database", checks);
    }

    [Fact]
    public async Task LiveHealth_ExcludesDatabaseCheck()
    {
        var client = factory.CreateClient();

        using var json = JsonDocument.Parse(await client.GetStringAsync("/health/live"));
        var checks = json.RootElement.GetProperty("checks").EnumerateArray()
            .Select(c => c.GetProperty("name").GetString())
            .ToList();

        Assert.Equal(["self"], checks);
    }
}
