"""Use cases: rebuild the co-purchase matrix, look up recommendations."""

import logging
import time
from collections.abc import Callable
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta

from app.config import Settings
from app.matrix import build_co_purchase_matrix
from app.repository import OrderHistoryRepository, Recommendation, RecommendationRepository

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class RefreshResult:
    computed_at: datetime
    orders: int
    products: int
    rows: int
    duration_ms: int
    stored: bool


class RecommendationService:
    def __init__(
        self,
        order_history: OrderHistoryRepository,
        store: RecommendationRepository,
        settings: Settings,
        clock: Callable[[], datetime] = lambda: datetime.now(UTC),
    ) -> None:
        self._order_history = order_history
        self._store = store
        self._settings = settings
        self._clock = clock
        self._last_refresh: RefreshResult | None = None

    @property
    def last_refresh(self) -> RefreshResult | None:
        return self._last_refresh

    def refresh(self) -> RefreshResult:
        started = time.perf_counter()
        computed_at = self._clock()
        since = computed_at - timedelta(days=self._settings.lookback_days) if self._settings.lookback_days else None

        lines = self._order_history.load_order_lines(since)
        matrix = build_co_purchase_matrix(lines, self._settings.top_n, self._settings.min_co_purchases)
        stored = self._store.replace_all(matrix, computed_at)

        result = RefreshResult(
            computed_at=computed_at,
            orders=int(lines["order_id"].nunique()),
            products=int(matrix["product_id"].nunique()),
            rows=len(matrix),
            duration_ms=round((time.perf_counter() - started) * 1000),
            stored=stored,
        )
        if stored:
            self._last_refresh = result
            log.info("Co-purchase matrix rebuilt: %s", result)
        else:
            log.info("Skipped storing the co-purchase matrix: another instance is refreshing")
        return result

    def recommendations_for(self, product_id: int, limit: int) -> list[Recommendation]:
        return self._store.find_for(product_id, min(limit, self._settings.top_n))

    def last_computed_at(self) -> datetime | None:
        return self._store.last_computed_at()
