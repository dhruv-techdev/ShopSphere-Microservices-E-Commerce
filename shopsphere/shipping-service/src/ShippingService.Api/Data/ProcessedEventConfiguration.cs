using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using ShippingService.Api.Domain;

namespace ShippingService.Api.Data;

internal sealed class ProcessedEventConfiguration : IEntityTypeConfiguration<ProcessedEvent>
{
    public void Configure(EntityTypeBuilder<ProcessedEvent> builder)
    {
        builder.ToTable("processed_events");

        builder.HasKey(e => e.EventId);
        builder.Property(e => e.EventId).HasColumnName("event_id")
            .HasMaxLength(ProcessedEvent.EventIdMaxLength).ValueGeneratedNever();
        builder.Property(e => e.EventType).HasColumnName("event_type")
            .HasMaxLength(ProcessedEvent.EventTypeMaxLength).IsRequired();
        builder.Property(e => e.ProcessedAt).HasColumnName("processed_at").IsRequired();

        builder.HasIndex(e => e.ProcessedAt).HasDatabaseName("idx_processed_events_processed_at");
    }
}
