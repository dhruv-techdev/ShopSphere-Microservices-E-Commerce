"""Eureka registration so the gateway can route to lb://recommendation-service."""

import asyncio
import logging

from py_eureka_client.eureka_client import EurekaClient

from app.config import Settings

log = logging.getLogger(__name__)

_MAX_BACKOFF_SECS = 60


class EurekaRegistration:
    """Registers in the background with retry, so a slow registry never blocks or kills startup."""

    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._client: EurekaClient | None = None
        self._task: asyncio.Task[None] | None = None

    def start(self) -> None:
        if self._settings.eureka_enabled and self._task is None:
            self._task = asyncio.create_task(self._register_with_retry(), name="eureka-registration")

    async def stop(self) -> None:
        if self._task is not None:
            self._task.cancel()
            await asyncio.gather(self._task, return_exceptions=True)
            self._task = None
        if self._client is not None:
            await self._client.stop()  # sends DOWN + deregisters
            self._client = None

    async def _register_with_retry(self) -> None:
        delay = 2
        while True:
            client = self._new_client()
            try:
                await client.start()
            except Exception as error:  # registry unreachable / rejected — keep serving, retry later
                log.warning("Eureka registration failed (%s); retrying in %ss", error, delay)
                await asyncio.sleep(delay)
                delay = min(delay * 2, _MAX_BACKOFF_SECS)
                continue
            self._client = client
            log.info("Registered %s with Eureka at %s", self._settings.app_name, self._settings.eureka_server)
            return

    def _new_client(self) -> EurekaClient:
        s = self._settings
        base_url = f"http://{s.instance_host}:{s.port}"
        return EurekaClient(
            eureka_server=s.eureka_server,
            app_name=s.app_name,
            instance_id=f"{s.app_name}:{s.instance_host}:{s.port}",
            instance_host=s.instance_host,
            instance_port=s.port,
            home_page_url=f"{base_url}/",
            status_page_url=f"{base_url}/health",
            health_check_url=f"{base_url}/health",
            renewal_interval_in_secs=s.eureka_renewal_interval_secs,
            should_discover=False,
        )
