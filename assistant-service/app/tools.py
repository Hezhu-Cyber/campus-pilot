from typing import Annotated, Any

from langchain_core.tools import tool
from pydantic import Field

from .java_client import JavaToolClient


def build_langchain_tools(
    client: JavaToolClient, include_write_actions: bool = False
) -> dict[str, Any]:
    """Build request-scoped tools backed by Spring APIs.

    Write preparation tools are only added to the deterministic action workflow.
    The read-only LLM agent never receives them.
    """

    @tool
    async def search_campus_activities(
        keyword: str = "",
        type_id: int | None = None,
        max_price: int | None = None,
        start_time: Annotated[
            str | None,
            Field(description="ISO-8601 date or datetime, e.g. 2026-09-15 or 2026-09-15T09:00:00"),
        ] = None,
        end_time: Annotated[
            str | None,
            Field(description="ISO-8601 date or datetime, e.g. 2026-09-20 or 2026-09-20T18:00:00"),
        ] = None,
        limit: Annotated[int, Field(ge=1, le=10)] = 5,
    ) -> list[dict[str, Any]]:
        """Search published campus activities with structured filters."""
        return await client.search_activities(
            keyword=keyword,
            type_id=type_id,
            max_price=max_price,
            start_time=start_time,
            end_time=end_time,
            limit=max(1, min(int(limit), 10)),
        )

    @tool
    async def get_campus_activity(activity_id: str) -> dict[str, Any]:
        """Load one published campus activity by its identifier."""
        return await client.get_activity(activity_id)

    @tool
    async def list_activity_categories() -> list[dict[str, Any]]:
        """List the activity categories available on CampusPilot."""
        return await client.list_activity_categories()

    @tool
    async def list_my_registrations() -> list[dict[str, Any]]:
        """List registrations belonging to the current authenticated user."""
        return await client.list_my_registrations()

    @tool
    async def list_hot_campus_posts(
        limit: Annotated[int, Field(ge=1, le=10)] = 5,
    ) -> list[dict[str, Any]]:
        """List popular posts from the campus community."""
        return await client.list_hot_posts(limit=max(1, min(int(limit), 10)))

    @tool
    async def list_registration_passes(activity_id: str) -> list[dict[str, Any]]:
        """List available registration passes for one activity."""
        return await client.list_registration_passes(activity_id)

    @tool
    async def list_my_support_tickets() -> list[dict[str, Any]]:
        """List the current user's human-support tickets."""
        return await client.list_my_support_tickets()

    tools: dict[str, Any] = {
        "search_campus_activities": search_campus_activities,
        "get_campus_activity": get_campus_activity,
        "list_activity_categories": list_activity_categories,
        "list_my_registrations": list_my_registrations,
        "list_hot_campus_posts": list_hot_campus_posts,
        "list_registration_passes": list_registration_passes,
        "list_my_support_tickets": list_my_support_tickets,
    }

    if include_write_actions:
        @tool
        async def prepare_registration_action(
            activity_id: str, registration_pass_id: str
        ) -> dict[str, Any]:
            """Create a one-time registration confirmation for the user."""
            return await client.prepare_registration(activity_id, registration_pass_id)

        @tool
        async def prepare_cancellation_action(registration_id: str) -> dict[str, Any]:
            """Create a one-time cancellation confirmation for the user."""
            return await client.prepare_cancellation(registration_id)

        tools["prepare_registration_action"] = prepare_registration_action
        tools["prepare_cancellation_action"] = prepare_cancellation_action

    return tools
