using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.AspNetCore.TestHost;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.DependencyInjection.Extensions;
using ShippingService.Api.Data;
using ShippingService.Api.Domain;

namespace ShippingService.Tests;

/// <summary>
/// Boots the real app in the "Testing" environment (Config Server + Eureka disabled via
/// appsettings.Testing.json) and swaps Postgres for an isolated in-memory database.
/// </summary>
public sealed class ShippingApiFactory : WebApplicationFactory<Program>
{
    private readonly string _databaseName = $"shipping-tests-{Guid.NewGuid()}";

    protected override void ConfigureWebHost(IWebHostBuilder builder)
    {
        builder.UseEnvironment("Testing");
        builder.ConfigureTestServices(services =>
        {
            services.RemoveAll<DbContextOptions<ShippingDbContext>>();
            services.AddDbContext<ShippingDbContext>(options => options.UseInMemoryDatabase(_databaseName));
        });
    }

    public async Task<Shipment> SeedAsync(Shipment shipment)
    {
        using var scope = Services.CreateScope();
        var db = scope.ServiceProvider.GetRequiredService<ShippingDbContext>();
        db.Shipments.Add(shipment);
        await db.SaveChangesAsync();
        return shipment;
    }
}
