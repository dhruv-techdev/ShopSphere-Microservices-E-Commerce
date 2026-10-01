from datetime import UTC, datetime

import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app
from app.repository import Recommendation

COMPUTED_AT = datetime(2026, 10, 1, 12, 0, tzinfo=UTC)


class StubService:
    def __init__(self) -> None:
        self.calls: list[tuple[int, int]] = []
        self.data = {
            7: [
                Recommendation(product_id=9, co_purchase_count=4, confidence=0.666666, lift=2.123456),
                Recommendation(product_id=3, co_purchase_count=1, confidence=0.1, lift=0.9),
            ]
        }

    def recommendations_for(self, product_id: int, limit: int):
        self.calls.append((product_id, limit))
        return self.data.get(product_id, [])[:limit]

    def last_computed_at(self):
        return COMPUTED_AT


@pytest.fixture
def stub() -> StubService:
    return StubService()


@pytest.fixture
def client(stub: StubService):
    app = create_app(Settings(eureka_enabled=False), service=stub)
    with TestClient(app) as test_client:
        yield test_client


def test_returns_ranked_recommendations_in_camel_case(client, stub):
    response = client.get("/api/v1/recommendations/7")

    assert response.status_code == 200
    assert response.json() == {
        "productId": 7,
        "recommendations": [
            {"productId": 9, "coPurchaseCount": 4, "confidence": 0.6667, "lift": 2.1235},
            {"productId": 3, "coPurchaseCount": 1, "confidence": 0.1, "lift": 0.9},
        ],
    }
    assert stub.calls == [(7, 5)]


def test_limit_is_passed_through(client, stub):
    response = client.get("/api/v1/recommendations/7", params={"limit": 1})

    assert [r["productId"] for r in response.json()["recommendations"]] == [9]
    assert stub.calls == [(7, 1)]


def test_unknown_product_gets_an_empty_list(client):
    response = client.get("/api/v1/recommendations/12345")

    assert response.status_code == 200
    assert response.json() == {"productId": 12345, "recommendations": []}


@pytest.mark.parametrize(
    "path, params",
    [
        ("/api/v1/recommendations/0", {}),
        ("/api/v1/recommendations/-4", {}),
        ("/api/v1/recommendations/abc", {}),
        ("/api/v1/recommendations/7", {"limit": 0}),
        ("/api/v1/recommendations/7", {"limit": 51}),
    ],
)
def test_rejects_invalid_input(client, stub, path, params):
    assert client.get(path, params=params).status_code == 422
    assert stub.calls == []


def test_gateway_health(client):
    response = client.get("/api/v1/recommendations/health")

    assert response.status_code == 200
    assert response.json() == {
        "service": "recommendation-service",
        "status": "UP",
        "lastRefreshAt": "2026-10-01T12:00:00Z",
    }


def test_eureka_health(client):
    assert client.get("/health").json() == {"status": "UP"}
