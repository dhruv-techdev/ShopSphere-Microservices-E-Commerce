from datetime import UTC, datetime, timedelta

import pandas as pd

from app.config import Settings
from app.repository import Recommendation
from app.service import RecommendationService

NOW = datetime(2026, 10, 1, 12, 0, tzinfo=UTC)


class FakeOrderHistory:
    def __init__(self, frame: pd.DataFrame) -> None:
        self.frame = frame
        self.since: datetime | None = None

    def load_order_lines(self, since):
        self.since = since
        return self.frame


class FakeStore:
    def __init__(self, acquire_lock: bool = True) -> None:
        self.acquire_lock = acquire_lock
        self.matrix: pd.DataFrame | None = None
        self.lookups: list[tuple[int, int]] = []

    def replace_all(self, matrix, computed_at):
        if self.acquire_lock:
            self.matrix = matrix
        return self.acquire_lock

    def find_for(self, product_id, limit):
        self.lookups.append((product_id, limit))
        return [Recommendation(product_id=2, co_purchase_count=3, confidence=0.5, lift=1.5)]

    def last_computed_at(self):
        return NOW


def history() -> pd.DataFrame:
    return pd.DataFrame({"order_id": [1, 1, 2, 2, 3], "product_id": [10, 20, 10, 30, 40]})


def service(store: FakeStore, orders: FakeOrderHistory, **overrides) -> RecommendationService:
    settings = Settings(eureka_enabled=False, **overrides)
    return RecommendationService(orders, store, settings, clock=lambda: NOW)


def test_refresh_builds_and_stores_the_matrix():
    store, orders = FakeStore(), FakeOrderHistory(history())

    result = service(store, orders, lookback_days=30).refresh()

    assert orders.since == NOW - timedelta(days=30)
    assert result.stored and result.computed_at == NOW
    assert (result.orders, result.products, result.rows) == (3, 3, 4)
    assert store.matrix is not None and len(store.matrix) == 4


def test_lookback_zero_reads_all_history():
    orders = FakeOrderHistory(history())

    service(FakeStore(), orders, lookback_days=0).refresh()

    assert orders.since is None


def test_refresh_reports_when_another_instance_holds_the_lock():
    recommender = service(FakeStore(acquire_lock=False), FakeOrderHistory(history()))

    result = recommender.refresh()

    assert not result.stored
    assert recommender.last_refresh is None


def test_lookup_limit_is_capped_by_top_n():
    store = FakeStore()

    found = service(store, FakeOrderHistory(history()), top_n=3).recommendations_for(10, 50)

    assert store.lookups == [(10, 3)]
    assert found[0].product_id == 2
