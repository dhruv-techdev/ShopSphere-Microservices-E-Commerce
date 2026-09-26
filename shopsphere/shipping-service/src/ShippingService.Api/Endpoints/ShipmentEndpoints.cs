using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using ShippingService.Api.Contracts;
using ShippingService.Api.Data;
using ShippingService.Api.Domain;

namespace ShippingService.Api.Endpoints;

/// <summary>
/// Read-only for now. Shipments are created from order/payment events in a later story.
/// X-User-Id is injected by the API Gateway from the JWT (same contract as order-service).
/// </summary>
public static class ShipmentEndpoints
{
    public static IEndpointRouteBuilder MapShipmentEndpoints(this IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/v1/shipments").WithTags("Shipments");

        group.MapGet("/{shipmentId:long}", GetByIdAsync)
            .WithName("GetShipmentById")
            .WithSummary("Get a shipment by id (owner only)");

        group.MapGet("/order/{orderId:long}", GetByOrderIdAsync)
            .WithName("GetShipmentByOrderId")
            .WithSummary("Get the shipment for an order (owner only)");

        return app;
    }

    internal static async Task<IResult> GetByIdAsync(
        long shipmentId,
        [FromHeader(Name = "X-User-Id")] long userId,
        ShippingDbContext db,
        CancellationToken ct)
    {
        var shipment = await db.Shipments.AsNoTracking()
            .FirstOrDefaultAsync(s => s.Id == shipmentId, ct);
        return ToResult(shipment, userId, $"Shipment {shipmentId} not found");
    }

    internal static async Task<IResult> GetByOrderIdAsync(
        long orderId,
        [FromHeader(Name = "X-User-Id")] long userId,
        ShippingDbContext db,
        CancellationToken ct)
    {
        var shipment = await db.Shipments.AsNoTracking()
            .FirstOrDefaultAsync(s => s.OrderId == orderId, ct);
        return ToResult(shipment, userId, $"No shipment found for order {orderId}");
    }

    private static IResult ToResult(Shipment? shipment, long userId, string notFoundMessage)
    {
        if (shipment is null)
        {
            return TypedResults.Problem(title: "Not Found", detail: notFoundMessage,
                statusCode: StatusCodes.Status404NotFound);
        }
        if (shipment.UserId != userId)
        {
            return TypedResults.Problem(title: "Forbidden", detail: "Shipment belongs to another user",
                statusCode: StatusCodes.Status403Forbidden);
        }
        return TypedResults.Ok(ShipmentResponse.From(shipment));
    }
}
