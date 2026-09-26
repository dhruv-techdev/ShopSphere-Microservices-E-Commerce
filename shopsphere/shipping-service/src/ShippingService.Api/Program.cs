using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.AspNetCore.Diagnostics.HealthChecks;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Diagnostics.HealthChecks;
using ShippingService.Api.Data;
using ShippingService.Api.Endpoints;
using ShippingService.Api.Health;
using Steeltoe.Discovery.Client;
using Steeltoe.Extensions.Configuration.ConfigServer;

var builder = WebApplication.CreateBuilder(args);

// ---- Spring Cloud Config client (config-service :8888) ----
// Pulls shared application.yml + shipping-service.yml. Toggle with spring:cloud:config:enabled.
builder.AddConfigServer();

// ---- Eureka registration + discovery (service-registry :8761) ----
builder.Services.AddDiscoveryClient(builder.Configuration);

// ---- EF Core + Postgres (schema shopsphere_shipping) ----
var connectionString = builder.Configuration.GetConnectionString("ShippingDb")
    ?? throw new InvalidOperationException("Connection string 'ShippingDb' is not configured.");

builder.Services.AddDbContext<ShippingDbContext>(options =>
    options.UseNpgsql(connectionString, npgsql =>
    {
        npgsql.MigrationsHistoryTable("__EFMigrationsHistory", ShippingDbContext.Schema);
        npgsql.EnableRetryOnFailure(maxRetryCount: 5);
    }));

// ---- Health checks ----
builder.Services.AddHealthChecks()
    .AddCheck("self", () => HealthCheckResult.Healthy("shipping-service is running"), tags: ["live"])
    .AddDbContextCheck<ShippingDbContext>("database", tags: ["ready"]);

// ---- HTTP plumbing ----
builder.Services.ConfigureHttpJsonOptions(options =>
    options.SerializerOptions.Converters.Add(new JsonStringEnumConverter(JsonNamingPolicy.SnakeCaseUpper)));
builder.Services.AddProblemDetails();
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();

var app = builder.Build();

app.UseExceptionHandler();
app.UseStatusCodePages();

app.UseSwagger();
app.UseSwaggerUI();

// /health            -> all checks (used by Eureka + Docker)
// /health/live       -> process only, no DB
// /api/v1/shipments/health -> same as /health, reachable through the API Gateway
app.MapHealthChecks("/health", new HealthCheckOptions { ResponseWriter = HealthResponseWriter.WriteAsync });
app.MapHealthChecks("/health/live", new HealthCheckOptions
{
    Predicate = r => r.Tags.Contains("live"),
    ResponseWriter = HealthResponseWriter.WriteAsync
});
app.MapHealthChecks("/api/v1/shipments/health", new HealthCheckOptions { ResponseWriter = HealthResponseWriter.WriteAsync });

app.MapShipmentEndpoints();

await ApplyMigrationsAsync(app);

await app.RunAsync();

static async Task ApplyMigrationsAsync(WebApplication app)
{
    using var scope = app.Services.CreateScope();
    var db = scope.ServiceProvider.GetRequiredService<ShippingDbContext>();
    if (db.Database.IsRelational())
    {
        await db.Database.MigrateAsync();
    }
}

// Exposed for WebApplicationFactory<Program> in the test project.
public partial class Program { }
