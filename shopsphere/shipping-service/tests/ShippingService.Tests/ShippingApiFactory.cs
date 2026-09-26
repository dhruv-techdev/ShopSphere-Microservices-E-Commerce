using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.AspNetCore.TestHost;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.DependencyInjection.Extensions;
using ShippingService.Api.Data;
using ShippingService.Api.Domain;
using ShippingService.Api.Messaging;

namespace ShippingService.Tests;

/// <summary>
/// Boots the real app in the "Testing" environment (Config Server, Eureka and the Kafka
/// consumer disabled via appsettings.Testing.json), swaps Postgres for an isolated in-memory
/// database and Kafka publishing for a fake.
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

            services.RemoveAll<IShipmentEventPublisher>();
            services.AddSingleton<IShipmentEventPublisher, FakeShipmentEventPublisher>();
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
