"""Postgres access: read order history (order-service schema), store/read recommendations (own schema)."""

from dataclasses import dataclass
from datetime import UTC, datetime
from typing import Any, cast

import pandas as pd
from sqlalchemy import Engine, text

from app.matrix import RESULT_DTYPES

# Orders whose goods were actually bought. Cancelled / failed / unpaid orders say nothing about taste.
PURCHASED_STATUSES = ("PAID", "SHIPPED", "DELIVERED")

# Arbitrary, stable key: only one replica rebuilds the table at a time.
REFRESH_LOCK_KEY = 6_300_001


@dataclass(frozen=True)
class Recommendation:
    product_id: int
    co_purchase_count: int
    confidence: float
    lift: float


class OrderHistoryRepository:
    """Read-only view of order-service's tables."""

    def __init__(self, engine: Engine, orders_schema: str) -> None:
        self._engine = engine
        self._query = text(
            f"""
            SELECT oi.order_id, oi.product_id
            FROM {orders_schema}.order_items oi
            JOIN {orders_schema}.orders o ON o.id = oi.order_id
            WHERE o.status = ANY(:statuses)
              AND (CAST(:since AS timestamptz) IS NULL OR o.created_at >= :since)
            """
        )

    def load_order_lines(self, since: datetime | None) -> pd.DataFrame:
        params: dict[str, Any] = {"statuses": list(PURCHASED_STATUSES), "since": since}
        with self._engine.connect() as connection:
            return pd.read_sql(
                self._query,
                connection,
                params=params,
                dtype={"order_id": "int64", "product_id": "int64"},
            )


class RecommendationRepository:
    def __init__(self, engine: Engine, schema: str) -> None:
        self._engine = engine
        self._schema = schema
        self._table = f"{schema}.product_recommendations"
        self._state = f"{schema}.matrix_state"

    def create_schema(self) -> None:
        with self._engine.begin() as connection:
            connection.execute(text(f"CREATE SCHEMA IF NOT EXISTS {self._schema}"))
            connection.execute(
                text(
                    f"""
                    CREATE TABLE IF NOT EXISTS {self._table} (
                        product_id             BIGINT           NOT NULL,
                        rank                   SMALLINT         NOT NULL,
                        recommended_product_id BIGINT           NOT NULL,
                        co_purchase_count      INTEGER          NOT NULL,
                        confidence             DOUBLE PRECISION NOT NULL,
                        lift                   DOUBLE PRECISION NOT NULL,
                        computed_at            TIMESTAMPTZ      NOT NULL,
                        PRIMARY KEY (product_id, rank)
                    )
                    """
                )
            )
            # Single row: when the matrix was last rebuilt (it may legitimately be empty).
            connection.execute(
                text(
                    f"""
                    CREATE TABLE IF NOT EXISTS {self._state} (
                        id          SMALLINT    PRIMARY KEY DEFAULT 1 CHECK (id = 1),
                        computed_at TIMESTAMPTZ NOT NULL,
                        row_count   INTEGER     NOT NULL
                    )
                    """
                )
            )

    def replace_all(self, matrix: pd.DataFrame, computed_at: datetime) -> bool:
        """
        Swaps the whole matrix in one transaction (readers keep seeing the previous one until commit).
        Returns False when another replica is already refreshing.
        """
        records = cast(list[dict[str, Any]], matrix.loc[:, list(RESULT_DTYPES)].to_dict("records"))
        rows = [{**record, "computed_at": computed_at} for record in records]
        with self._engine.begin() as connection:
            locked = connection.execute(
                text("SELECT pg_try_advisory_xact_lock(:key)"), {"key": REFRESH_LOCK_KEY}
            ).scalar_one()
            if not locked:
                return False
            connection.execute(text(f"DELETE FROM {self._table}"))
            if rows:
                connection.execute(
                    text(
                        f"""
                        INSERT INTO {self._table}
                            (product_id, rank, recommended_product_id, co_purchase_count, confidence, lift, computed_at)
                        VALUES
                            (:product_id, :rank, :recommended_product_id, :co_purchase_count, :confidence, :lift,
                             :computed_at)
                        """
                    ),
                    rows,
                )
            connection.execute(
                text(
                    f"""
                    INSERT INTO {self._state} (id, computed_at, row_count) VALUES (1, :computed_at, :row_count)
                    ON CONFLICT (id) DO UPDATE SET computed_at = EXCLUDED.computed_at, row_count = EXCLUDED.row_count
                    """
                ),
                {"computed_at": computed_at, "row_count": len(rows)},
            )
        return True

    def find_for(self, product_id: int, limit: int) -> list[Recommendation]:
        with self._engine.connect() as connection:
            result = connection.execute(
                text(
                    f"""
                    SELECT recommended_product_id, co_purchase_count, confidence, lift
                    FROM {self._table}
                    WHERE product_id = :product_id
                    ORDER BY rank
                    LIMIT :limit
                    """
                ),
                {"product_id": product_id, "limit": limit},
            )
            return [
                Recommendation(
                    product_id=row.recommended_product_id,
                    co_purchase_count=row.co_purchase_count,
                    confidence=row.confidence,
                    lift=row.lift,
                )
                for row in result
            ]

    def last_computed_at(self) -> datetime | None:
        with self._engine.connect() as connection:
            computed_at = connection.execute(text(f"SELECT max(computed_at) FROM {self._state}")).scalar_one()
        return computed_at.astimezone(UTC) if computed_at else None
