using System;
using Microsoft.EntityFrameworkCore.Migrations;
using Npgsql.EntityFrameworkCore.PostgreSQL.Metadata;

#nullable disable

namespace ShippingService.Api.Data.Migrations
{
    /// <inheritdoc />
    public partial class InitialCreate : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.EnsureSchema(
                name: "shopsphere_shipping");

            migrationBuilder.CreateTable(
                name: "processed_events",
                schema: "shopsphere_shipping",
                columns: table => new
                {
                    event_id = table.Column<string>(type: "character varying(100)", maxLength: 100, nullable: false),
                    event_type = table.Column<string>(type: "character varying(100)", maxLength: 100, nullable: false),
                    processed_at = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_processed_events", x => x.event_id);
                });

            migrationBuilder.CreateTable(
                name: "shipments",
                schema: "shopsphere_shipping",
                columns: table => new
                {
                    id = table.Column<long>(type: "bigint", nullable: false)
                        .Annotation("Npgsql:ValueGenerationStrategy", NpgsqlValueGenerationStrategy.IdentityByDefaultColumn),
                    order_id = table.Column<long>(type: "bigint", nullable: false),
                    user_id = table.Column<long>(type: "bigint", nullable: false),
                    status = table.Column<string>(type: "character varying(30)", maxLength: 30, nullable: false),
                    carrier = table.Column<string>(type: "character varying(100)", maxLength: 100, nullable: true),
                    tracking_number = table.Column<string>(type: "character varying(100)", maxLength: 100, nullable: true),
                    shipping_recipient_name = table.Column<string>(type: "character varying(100)", maxLength: 100, nullable: false),
                    shipping_phone = table.Column<string>(type: "character varying(20)", maxLength: 20, nullable: true),
                    shipping_line1 = table.Column<string>(type: "character varying(200)", maxLength: 200, nullable: false),
                    shipping_line2 = table.Column<string>(type: "character varying(200)", maxLength: 200, nullable: true),
                    shipping_city = table.Column<string>(type: "character varying(100)", maxLength: 100, nullable: false),
                    shipping_state = table.Column<string>(type: "character varying(100)", maxLength: 100, nullable: true),
                    shipping_postal_code = table.Column<string>(type: "character varying(20)", maxLength: 20, nullable: false),
                    shipping_country = table.Column<string>(type: "character varying(2)", maxLength: 2, nullable: false),
                    created_at = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false),
                    updated_at = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false),
                    shipped_at = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: true),
                    delivered_at = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: true),
                    dispatch_published_at = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: true),
                    delivered_published_at = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_shipments", x => x.id);
                });

            migrationBuilder.CreateIndex(
                name: "idx_processed_events_processed_at",
                schema: "shopsphere_shipping",
                table: "processed_events",
                column: "processed_at");

            migrationBuilder.CreateIndex(
                name: "idx_shipments_status",
                schema: "shopsphere_shipping",
                table: "shipments",
                column: "status");

            migrationBuilder.CreateIndex(
                name: "idx_shipments_user",
                schema: "shopsphere_shipping",
                table: "shipments",
                column: "user_id");

            migrationBuilder.CreateIndex(
                name: "uk_shipments_order",
                schema: "shopsphere_shipping",
                table: "shipments",
                column: "order_id",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "uk_shipments_tracking_number",
                schema: "shopsphere_shipping",
                table: "shipments",
                column: "tracking_number",
                unique: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "processed_events",
                schema: "shopsphere_shipping");

            migrationBuilder.DropTable(
                name: "shipments",
                schema: "shopsphere_shipping");
        }
    }
}
