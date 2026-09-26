using Microsoft.EntityFrameworkCore;
using ShippingService.Api.Domain;

namespace ShippingService.Api.Data;

public sealed class ShippingDbContext(DbContextOptions<ShippingDbContext> options) : DbContext(options)
{
    /// <summary>Schema-per-service inside the shared "shopsphere" database, like the Java services.</summary>
    public const string Schema = "shopsphere_shipping";

    public DbSet<Shipment> Shipments => Set<Shipment>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.HasDefaultSchema(Schema);
        modelBuilder.ApplyConfigurationsFromAssembly(typeof(ShippingDbContext).Assembly);
    }

    public override int SaveChanges(bool acceptAllChangesOnSuccess)
    {
        StampTimestamps();
        return base.SaveChanges(acceptAllChangesOnSuccess);
    }

    public override Task<int> SaveChangesAsync(bool acceptAllChangesOnSuccess, CancellationToken cancellationToken = default)
    {
        StampTimestamps();
        return base.SaveChangesAsync(acceptAllChangesOnSuccess, cancellationToken);
    }

    private void StampTimestamps()
    {
        var now = DateTimeOffset.UtcNow;
        foreach (var entry in ChangeTracker.Entries<Shipment>())
        {
            if (entry.State == EntityState.Added)
            {
                entry.Property(s => s.CreatedAt).CurrentValue = now;
                entry.Property(s => s.UpdatedAt).CurrentValue = now;
            }
            else if (entry.State == EntityState.Modified)
            {
                entry.Property(s => s.UpdatedAt).CurrentValue = now;
            }
        }
    }
}
