"""Periodic rebuild of the co-purchase matrix."""

import asyncio
import logging
from datetime import UTC, datetime

from apscheduler.schedulers.asyncio import AsyncIOScheduler
from apscheduler.triggers.interval import IntervalTrigger

from app.config import Settings
from app.service import RecommendationService

log = logging.getLogger(__name__)

JOB_ID = "co-purchase-refresh"


def create_scheduler(service: RecommendationService, settings: Settings) -> AsyncIOScheduler:
    scheduler = AsyncIOScheduler(timezone=UTC)
    # Passing next_run_time=None would add the job paused, so only set it to run immediately.
    first_run = {"next_run_time": datetime.now(UTC)} if settings.refresh_on_startup else {}
    scheduler.add_job(
        _refresh,
        IntervalTrigger(minutes=settings.refresh_interval_minutes),
        args=[service],
        id=JOB_ID,
        max_instances=1,  # a slow run is never overlapped by the next tick
        coalesce=True,
        misfire_grace_time=60,
        **first_run,
    )
    return scheduler


async def _refresh(service: RecommendationService) -> None:
    try:
        # pandas + blocking DB I/O stay off the event loop so API requests keep flowing.
        await asyncio.to_thread(service.refresh)
    except Exception:
        log.exception("Co-purchase refresh failed; keeping the previous matrix")
