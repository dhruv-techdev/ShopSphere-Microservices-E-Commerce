"""HTTP API. Routed by the gateway as GET /api/v1/recommendations/** (public, like the catalog)."""

from datetime import datetime
from typing import Annotated

from fastapi import APIRouter, Depends, Path, Query, Request
from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel

from app.service import RecommendationService

router = APIRouter(prefix="/api/v1/recommendations", tags=["Recommendations"])


class CamelModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class RecommendedProduct(CamelModel):
    product_id: int
    co_purchase_count: int
    confidence: float
    lift: float


class RecommendationsResponse(CamelModel):
    product_id: int
    recommendations: list[RecommendedProduct]


class HealthResponse(CamelModel):
    service: str
    status: str
    last_refresh_at: datetime | None


def get_service(request: Request) -> RecommendationService:
    return request.app.state.service


ServiceDep = Annotated[RecommendationService, Depends(get_service)]


@router.get("/health", response_model=HealthResponse, response_model_by_alias=True)
def health(service: ServiceDep) -> HealthResponse:
    """Also proves the database is reachable; lastRefreshAt is null until the first job run."""
    return HealthResponse(service="recommendation-service", status="UP", last_refresh_at=service.last_computed_at())


@router.get(
    "/{product_id}",
    response_model=RecommendationsResponse,
    response_model_by_alias=True,
    summary="Products frequently bought together with this one",
    description=(
        "Ranked by how many paid orders contained both products, then by lift. "
        "Unknown products, or products never bought with anything else, return an empty list."
    ),
)
def recommendations(
    service: ServiceDep,
    product_id: Annotated[int, Path(gt=0, description="Catalog product id")],
    limit: Annotated[int, Query(ge=1, le=50, description="Max results (capped by TOP_N)")] = 5,
) -> RecommendationsResponse:
    found = service.recommendations_for(product_id, limit)
    return RecommendationsResponse(
        product_id=product_id,
        recommendations=[
            RecommendedProduct(
                product_id=r.product_id,
                co_purchase_count=r.co_purchase_count,
                confidence=round(r.confidence, 4),
                lift=round(r.lift, 4),
            )
            for r in found
        ],
    )
