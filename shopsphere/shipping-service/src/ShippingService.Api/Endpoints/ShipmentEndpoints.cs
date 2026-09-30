using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using ShippingService.Api.Contracts;
using ShippingService.Api.Data;
using ShippingService.Api.Domain;
using ShippingService.Api.Shipping;

namespace ShippingService.Api.Endpoints;

/// <summary>
/// X-User-Id / X-User-Role are injected by the API Gateway from the JWT.
/// Reads: owner (or ADMIN). Transitions: ADMIN only — enforced at the gateway AND here.
/// </summary>
public static class ShipmentEndpoints
{
    private const string AdminRole = "ADMIN";

    public static IEndpointRouteBuilder MapShipmentEndpoints(this IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/v1/shipments").WithTags("Shipments");

        group.MapGet("/{shipmentId:long}", GetByIdAsync)
            .WithName("GetShipmentById")
            .WithSummary("Get a shipment by id (owner or ADMIN)");

        group.MapGet("/order/{orderId:long}", GetByOrderIdAsync)
            .WithName("GetShipmentByOrderId")
            .WithSummary("Get the shipment for an order (owner or ADMIN)");

        group.MapPost("/{shipmentId:long}/deliver", DeliverAsync)
            .WithName("DeliverShipment")
            .WithSummary("Mark a SHIPPED shipment as DELIVERED and publish shipment.delivered (ADMIN)");

        group.MapPost("/{shipmentId:long}/cancel", CancelAsync)
            .WithName("CancelShipment")
            .WithSummary("Cancel a PENDING shipment (ADMIN)");

        return app;
    }

    /* ------------------------------ reads ------------------------------ */

    internal static async Task<IResult> GetByIdAsync(
        long shipmentId,
        [FromHeader(Name = "X-User-Id")] long userId,
        [FromHeader(Name = "X-User-Role")] string? role,
        ShippingDbContext db,
        CancellationToken ct)
    {
        var shipment = await db.Shipments.AsNoTracking()
            .FirstOrDefaultAsync(s => s.Id == shipmentId, ct);
        return ToReadResult(shipment, userId, role, $"Shipment {shipmentId} not found");
    }

    internal static async Task<IResult> GetByOrderIdAsync(
        long orderId,
        [FromHeader(Name = "X-User-Id")] long userId,
        [FromHeader(Name = "X-User-Role")] string? role,
        ShippingDbContext db,
        CancellationToken ct)
    {
        var shipment = await db.Shipments.AsNoTracking()
            .FirstOrDefaultAsync(s => s.OrderId == orderId, ct);
        return ToReadResult(shipment, userId, role, $"No shipment found for order {orderId}");
    }

    /* --------------------------- transitions --------------------------- */

    internal static async Task<IResult> DeliverAsync(
        long shipmentId,
        [FromHeader(Name = "X-User-Role")] string? role,
        ShipmentLifecycleService lifecycle,
        CancellationToken ct)
    {
        if (!IsAdmin(role))
        {
            return Forbidden("ADMIN role required to change shipment status");
        }
        return ToTransitionResult(await lifecycle.DeliverAsync(shipmentId, ct));
    }

    internal static async Task<IResult> CancelAsync(
        long shipmentId,
        [FromHeader(Name = "X-User-Role")] string? role,
        ShipmentLifecycleService lifecycle,
        CancellationToken ct)
    {
        if (!IsAdmin(role))
        {
            return Forbidden("ADMIN role required to change shipment status");
        }
        return ToTransitionResult(await lifecycle.CancelAsync(shipmentId, ct));
    }

    /* ----------------------------- helpers ----------------------------- */

    private static bool IsAdmin(string? role) => string.Equals(role, AdminRole, StringComparison.OrdinalIgnoreCase);

    private static IResult Forbidden(string detail) =>
        TypedResults.Problem(title: "Forbidden", detail: detail, statusCode: StatusCodes.Status403Forbidden);

    private static IResult ToReadResult(Shipment? shipment, long userId, string? role, string notFoundMessage)
    {
        if (shipment is null)
        {
            return TypedResults.Problem(title: "Not Found", detail: notFoundMessage,
                statusCode: StatusCodes.Status404NotFound);
        }
        if (shipment.UserId != userId && !IsAdmin(role))
        {
            return Forbidden("Shipment belongs to another user");
        }
        return TypedResults.Ok(ShipmentResponse.From(shipment));
    }

    private static IResult ToTransitionResult(TransitionResult result) => result.Status switch
    {
        TransitionStatus.Ok => TypedResults.Ok(ShipmentResponse.From(result.Shipment!)),
        TransitionStatus.NotFound => TypedResults.Problem(title: "Not Found", detail: result.Error,
            statusCode: StatusCodes.Status404NotFound),
        _ => TypedResults.Problem(title: "Conflict", detail: result.Error,
            statusCode: StatusCodes.Status409Conflict)
    };
}
