from typing import Any

import httpx

from .config import Settings


class JavaToolError(RuntimeError):
    """Raised when the Java business tool API cannot serve a request."""


class JavaToolClient:
    """Authenticated client for Spring assistant tools."""

    def __init__(self, settings: Settings, user_context_token: str) -> None:
        self._settings = settings
        self._client = httpx.AsyncClient(
            base_url=settings.java_base_url.rstrip("/"),
            timeout=settings.request_timeout_seconds,
            headers={
                "X-Assistant-Internal-Token": settings.internal_token,
                "X-Assistant-User-Context": user_context_token,
            },
        )

    async def close(self) -> None:
        await self._client.aclose()

    async def search_activities(
        self,
        keyword: str = "",
        type_id: int | None = None,
        max_price: int | None = None,
        start_time: str | None = None,
        end_time: str | None = None,
        limit: int = 5,
    ) -> list[dict[str, Any]]:
        return await self._get(
            "/internal/assistant/tools/activities/search",
            {
                "keyword": keyword or None,
                "typeId": type_id,
                "maxPrice": max_price,
                "startTime": start_time,
                "endTime": end_time,
                "limit": max(1, min(int(limit), 10)),
            },
        )

    async def get_activity(self, activity_id: str) -> dict[str, Any]:
        return await self._get(f"/internal/assistant/tools/activities/{activity_id}")

    async def list_activity_categories(self) -> list[dict[str, Any]]:
        return await self._get("/internal/assistant/tools/activity-categories")

    async def list_my_registrations(self) -> list[dict[str, Any]]:
        return await self._get("/internal/assistant/tools/registrations/mine")

    async def list_hot_posts(self, limit: int = 5) -> list[dict[str, Any]]:
        return await self._get(
            "/internal/assistant/tools/posts/hot",
            {"limit": max(1, min(int(limit), 10))},
        )

    async def current_profile(self) -> dict[str, Any]:
        return await self._get("/internal/assistant/tools/profile")

    async def list_registration_passes(self, activity_id: str) -> list[dict[str, Any]]:
        return await self._get(
            f"/internal/assistant/tools/activities/{activity_id}/registration-passes"
        )

    async def prepare_registration(
        self, activity_id: str, registration_pass_id: str
    ) -> dict[str, Any]:
        return await self._post(
            "/internal/assistant/tools/actions/register/prepare",
            {
                "activityId": activity_id,
                "registrationPassId": registration_pass_id,
            },
        )

    async def prepare_cancellation(self, registration_id: str) -> dict[str, Any]:
        return await self._post(
            "/internal/assistant/tools/actions/cancel/prepare",
            {"registrationId": registration_id},
        )

    async def list_my_support_tickets(self) -> list[dict[str, Any]]:
        result = await self._get("/internal/assistant/tools/support-tickets/mine")
        if isinstance(result, dict) and result.get("success"):
            data = result.get("data")
            return data if isinstance(data, list) else []
        if isinstance(result, dict):
            raise JavaToolError(result.get("errorMsg") or "support ticket query failed")
        return result if isinstance(result, list) else []

    async def create_support_ticket(
        self, thread_id: str, category: str, subject: str, content: str
    ) -> dict[str, Any]:
        return await self._post(
            "/internal/assistant/tools/support-tickets",
            {
                "threadId": thread_id,
                "category": category,
                "subject": subject,
                "content": content,
            },
        )

    async def _post(self, path: str, payload: dict[str, Any]) -> Any:
        try:
            response = await self._client.post(path, json=payload)
            response.raise_for_status()
            return response.json()
        except (httpx.HTTPError, ValueError) as exc:
            raise JavaToolError(f"Java tool request failed: {path}") from exc

    async def _get(self, path: str, params: dict[str, Any] | None = None) -> Any:
        try:
            response = await self._client.get(path, params=params)
            response.raise_for_status()
            return response.json()
        except (httpx.HTTPError, ValueError) as exc:
            raise JavaToolError(f"Java tool request failed: {path}") from exc
