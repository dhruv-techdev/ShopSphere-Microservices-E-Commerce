"""Runtime settings, read from environment variables (see docker-compose.yml)."""

import socket
from functools import lru_cache

from pydantic import Field, PositiveInt
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    app_name: str = "recommendation-service"
    port: PositiveInt = 8089
    log_level: str = "INFO"

    # Postgres: order history is read from order-service's schema; results live in our own schema.
    database_url: str = "postgresql+psycopg://shopsphere:shopsphere@localhost:5432/shopsphere"
    orders_schema: str = Field(default="shopsphere_orders", pattern=r"^[a-z_][a-z0-9_]*$")
    schema_name: str = Field(default="shopsphere_recommendations", pattern=r"^[a-z_][a-z0-9_]*$")

    # Co-purchase job
    refresh_interval_minutes: PositiveInt = 15
    refresh_on_startup: bool = True
    lookback_days: int = Field(default=365, ge=0, description="0 = all order history")
    top_n: PositiveInt = Field(default=10, le=100)
    min_co_purchases: PositiveInt = 1

    # Eureka
    eureka_enabled: bool = True
    eureka_server: str = "http://localhost:8761/eureka/"
    instance_host: str = Field(default_factory=socket.gethostname)
    eureka_renewal_interval_secs: PositiveInt = 30


@lru_cache
def get_settings() -> Settings:
    return Settings()
