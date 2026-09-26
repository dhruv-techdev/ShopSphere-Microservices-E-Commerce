using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using ShippingService.Api.Domain;

namespace ShippingService.Api.Data;

internal sealed class ShipmentConfiguration : IEntityTypeConfiguration<Shipment>
{
    public void Configure(EntityTypeBuilder<Shipment> builder)
    {
        builder.ToTable("shipments");

        builder.HasKey(s => s.Id);
        builder.Property(s => s.Id).HasColumnName("id");

        builder.Property(s => s.OrderId).HasColumnName("order_id").IsRequired();
        builder.HasIndex(s => s.OrderId).IsUnique().HasDatabaseName("uk_shipments_order");

        builder.Property(s => s.UserId).HasColumnName("user_id").IsRequired();
        builder.HasIndex(s => s.UserId).HasDatabaseName("idx_shipments_user");

        builder.Property(s => s.Status)
            .HasColumnName("status")
            .HasMaxLength(30)
            .HasConversion(
                v => v.ToString().ToUpperInvariant(),
                v => Enum.Parse<ShipmentStatus>(v, true))
            .IsRequired();
        builder.HasIndex(s => s.Status).HasDatabaseName("idx_shipments_status");

        builder.Property(s => s.Carrier).HasColumnName("carrier").HasMaxLength(100);
        builder.Property(s => s.TrackingNumber).HasColumnName("tracking_number").HasMaxLength(100);

        builder.Property(s => s.CreatedAt).HasColumnName("created_at").IsRequired();
        builder.Property(s => s.UpdatedAt).HasColumnName("updated_at").IsRequired();
        builder.Property(s => s.ShippedAt).HasColumnName("shipped_at");
        builder.Property(s => s.DeliveredAt).HasColumnName("delivered_at");

        builder.OwnsOne(s => s.ShippingAddress, address =>
        {
            address.Property(a => a.RecipientName).HasColumnName("shipping_recipient_name")
                .HasMaxLength(ShippingAddress.RecipientNameMaxLength).IsRequired();
            address.Property(a => a.Phone).HasColumnName("shipping_phone")
                .HasMaxLength(ShippingAddress.PhoneMaxLength);
            address.Property(a => a.Line1).HasColumnName("shipping_line1")
                .HasMaxLength(ShippingAddress.LineMaxLength).IsRequired();
            address.Property(a => a.Line2).HasColumnName("shipping_line2")
                .HasMaxLength(ShippingAddress.LineMaxLength);
            address.Property(a => a.City).HasColumnName("shipping_city")
                .HasMaxLength(ShippingAddress.CityMaxLength).IsRequired();
            address.Property(a => a.State).HasColumnName("shipping_state")
                .HasMaxLength(ShippingAddress.StateMaxLength);
            address.Property(a => a.PostalCode).HasColumnName("shipping_postal_code")
                .HasMaxLength(ShippingAddress.PostalCodeMaxLength).IsRequired();
            address.Property(a => a.Country).HasColumnName("shipping_country")
                .HasMaxLength(ShippingAddress.CountryLength).IsRequired();
        });
        builder.Navigation(s => s.ShippingAddress).IsRequired();
    }
}
