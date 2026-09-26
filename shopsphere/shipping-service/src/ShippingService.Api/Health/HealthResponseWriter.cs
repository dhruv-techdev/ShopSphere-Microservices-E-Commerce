using System.Text.Json;
using Microsoft.Extensions.Diagnostics.HealthChecks;

namespace ShippingService.Api.Health;

/// <summary>Spring-style health JSON (UP / DOWN) so all services report the same way.</summary>
public static class HealthResponseWriter
{
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web);

    public static Task WriteAsync(HttpContext context, HealthReport report)
    {
        context.Response.ContentType = "application/json; charset=utf-8";

        var payload = new
        {
            service = "shipping-service",
            status = ToStatus(report.Status),
            totalDurationMs = Math.Round(report.TotalDuration.TotalMilliseconds, 2),
            checks = report.Entries.Select(e => new
            {
                name = e.Key,
                status = ToStatus(e.Value.Status),
                description = e.Value.Description,
                durationMs = Math.Round(e.Value.Duration.TotalMilliseconds, 2)
            })
        };

        return context.Response.WriteAsync(JsonSerializer.Serialize(payload, JsonOptions));
    }

    public static string ToStatus(HealthStatus status) => status switch
    {
        HealthStatus.Healthy => "UP",
        HealthStatus.Degraded => "DEGRADED",
        _ => "DOWN"
    };
}
