"""Runs against a real Postgres when TEST_DATABASE_URL is set (CI provides one); skipped otherwise."""

import os
import uuid
from datetime import UTC, datetime, timedelta

import pandas as pd
import pytest
from sqlalchemy import create_engine, text

from app.matrix import build_co_purchase_matrix
from app.repository import REFRESH_LOCK_KEY, OrderHistoryRepository, Recommendation, RecommendationRepository

DATABASE_URL = os.getenv("TEST_DATABASE_URL")
pytestmark = pytest.mark.skipif(not DATABASE_URL, reason="TEST_DATABASE_URL not set")

NOW = datetime(2026, 10, 1, 12, 0, tzinfo=UTC)


@pytest.fixture
def schemas():
    """Throwaway schemas per test: an order-service lookalike and our own."""
    engine = create_engine(DATABASE_URL)
    suffix = uuid.uuid4().hex[:8]
    orders_schema, own_schema = f"test_orders_{suffix}", f"test_recs_{suffix}"
    with engine.begin() as connection:
        connection.execute(text(f"CREATE SCHEMA {orders_schema}"))
        connection.execute(
            text(
                f"""
                CREATE TABLE {orders_schema}.orders (
                    id BIGINT PRIMARY KEY, status VARCHAR(30) NOT NULL, created_at TIMESTAMPTZ
                );
                CREATE TABLE {orders_schema}.order_items (
                    id BIGSERIAL PRIMARY KEY, order_id BIGINT NOT NULL, product_id BIGINT NOT NULL
                );
                """
            )
        )
    yield engine, orders_schema, own_schema
    with engine.begin() as connection:
        connection.execute(text(f"DROP SCHEMA IF EXISTS {orders_schema} CASCADE"))
        connection.execute(text(f"DROP SCHEMA IF EXISTS {own_schema} CASCADE"))
    engine.dispose()


def insert_order(engine, schema, order_id, status, products, created_at=NOW):
    with engine.begin() as connection:
        connection.execute(
            text(f"INSERT INTO {schema}.orders (id, status, created_at) VALUES (:id, :status, :created_at)"),
            {"id": order_id, "status": status, "created_at": created_at},
        )
        connection.execute(
            text(f"INSERT INTO {schema}.order_items (order_id, product_id) VALUES (:order_id, :product_id)"),
            [{"order_id": order_id, "product_id": product_id} for product_id in products],
        )


def test_reads_only_purchased_orders_inside_the_window(schemas):
    engine, orders_schema, _ = schemas
    insert_order(engine, orders_schema, 1, "PAID", [10, 20])
    insert_order(engine, orders_schema, 2, "DELIVERED", [10, 30])
    insert_order(engine, orders_schema, 3, "CANCELLED", [10, 40])
    insert_order(engine, orders_schema, 4, "PENDING_PAYMENT", [10, 50])
    insert_order(engine, orders_schema, 5, "SHIPPED", [10, 60], created_at=NOW - timedelta(days=400))

    history = OrderHistoryRepository(engine, orders_schema)

    recent = history.load_order_lines(since=NOW - timedelta(days=365))
    everything = history.load_order_lines(since=None)

    assert sorted(recent["order_id"].unique().tolist()) == [1, 2]
    assert sorted(everything["order_id"].unique().tolist()) == [1, 2, 5]
    assert str(recent["product_id"].dtype) == "int64"


def test_replace_all_swaps_the_matrix_and_serves_it_ranked(schemas):
    engine, orders_schema, own_schema = schemas
    insert_order(engine, orders_schema, 1, "PAID", [10, 20])
    insert_order(engine, orders_schema, 2, "PAID", [10, 20, 30])
    store = RecommendationRepository(engine, own_schema)
    store.create_schema()
    store.create_schema()  # idempotent
    assert store.last_computed_at() is None

    matrix = build_co_purchase_matrix(OrderHistoryRepository(engine, orders_schema).load_order_lines(None), top_n=5)
    assert store.replace_all(matrix, NOW)

    assert store.find_for(10, limit=5) == [
        Recommendation(product_id=20, co_purchase_count=2, confidence=1.0, lift=1.0),
        Recommendation(product_id=30, co_purchase_count=1, confidence=0.5, lift=1.0),
    ]
    assert [r.product_id for r in store.find_for(10, limit=1)] == [20]
    assert store.find_for(999, limit=5) == []
    assert store.last_computed_at() == NOW

    later = NOW + timedelta(minutes=15)
    assert store.replace_all(matrix.iloc[0:0], later)
    assert store.find_for(10, limit=5) == []
    assert store.last_computed_at() == later


def test_refresh_is_skipped_while_another_instance_holds_the_lock(schemas):
    engine, _, own_schema = schemas
    store = RecommendationRepository(engine, own_schema)
    store.create_schema()

    with engine.begin() as other_replica:
        other_replica.execute(text("SELECT pg_advisory_xact_lock(:key)"), {"key": REFRESH_LOCK_KEY})
        lines = pd.DataFrame({"order_id": [1, 1], "product_id": [1, 2]})
        assert store.replace_all(build_co_purchase_matrix(lines, top_n=5), NOW) is False
