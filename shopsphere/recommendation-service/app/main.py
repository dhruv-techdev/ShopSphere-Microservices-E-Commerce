"""FastAPI application: wiring, lifespan (schema, scheduler, Eureka) and the Eureka health endpoint."""

import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI
from sqlalchemy import create_engine

from app.api import router
from app.config import Settings, get_settings
from app.eureka import EurekaRegistration
from app.repository import OrderHistoryRepository, RecommendationRepository
from app.scheduler import create_scheduler
from app.service import RecommendationService


def create_app(settings: Settings | None = None, service: RecommendationService | None = None) -> FastAPI:
    """``service`` is injectable for tests; when given, no database, scheduler or Eureka is started."""
    settings = settings or get_settings()
    logging.basicConfig(level=settings.log_level, format="%(asctime)s %(levelname)s [%(name)s] %(message)s")

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        if service is not None:
            app.state.service = service
            yield
            return

        engine = create_engine(settings.database_url, pool_pre_ping=True, pool_size=5)
        store = RecommendationRepository(engine, settings.schema_name)
        store.create_schema()
        app.state.service = RecommendationService(
            OrderHistoryRepository(engine, settings.orders_schema), store, settings
        )

        scheduler = create_scheduler(app.state.service, settings)
        eureka = EurekaRegistration(settings)
        scheduler.start()
        eureka.start()
        try:
            yield
        finally:
            await eureka.stop()
            scheduler.shutdown(wait=False)
            engine.dispose()

    app = FastAPI(
        title="ShopSphere Recommendation Service",
        version="1.0.0",
        lifespan=lifespan,
        docs_url="/api/v1/recommendations/docs",
        openapi_url="/api/v1/recommendations/openapi.json",
    )
    app.include_router(router)

    @app.get("/health", include_in_schema=False)
    def eureka_health() -> dict[str, str]:
        return {"status": "UP"}

    return app
