using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Design;

namespace ShippingService.Api.Data;

/// <summary>
/// Used only by `dotnet ef` at design time so migrations don't boot the app
/// (and don't try to reach Eureka / Config Server).
/// </summary>
internal sealed class ShippingDbContextFactory : IDesignTimeDbContextFactory<ShippingDbContext>
{
    public ShippingDbContext CreateDbContext(string[] args)
    {
        var connectionString = Environment.GetEnvironmentVariable("ConnectionStrings__ShippingDb")
            ?? "Host=localhost;Port=5432;Database=shopsphere;Username=shopsphere;Password=shopsphere";

        var options = new DbContextOptionsBuilder<ShippingDbContext>()
            .UseNpgsql(connectionString, npgsql =>
                npgsql.MigrationsHistoryTable("__EFMigrationsHistory", ShippingDbContext.Schema))
            .Options;

        return new ShippingDbContext(options);
    }
}
