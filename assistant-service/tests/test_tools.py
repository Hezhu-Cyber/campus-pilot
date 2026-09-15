import asyncio

import pytest

from app.tools import build_langchain_tools


class FakeJavaClient:
    async def search_activities(self, **kwargs):
        return [{"id": "1", "name": "人工智能前沿讲座", "filters": kwargs}]


def test_langchain_tool_invocation() -> None:
    tools = build_langchain_tools(FakeJavaClient())
    result = asyncio.run(
        tools["search_campus_activities"].ainvoke(
            {
                "keyword": "人工智能",
                "type_id": 1,
                "max_price": 0,
                "start_time": None,
                "end_time": None,
                "limit": 5,
            }
        )
    )
    assert result[0]["name"] == "人工智能前沿讲座"
    assert result[0]["filters"]["type_id"] == 1


def test_write_tools_are_not_exposed_to_read_agent() -> None:
    tools = build_langchain_tools(FakeJavaClient())
    assert "prepare_registration_action" not in tools
    assert "prepare_cancellation_action" not in tools


def test_tool_schema_rejects_excessive_limit() -> None:
    tools = build_langchain_tools(FakeJavaClient())
    with pytest.raises(Exception):
        asyncio.run(
            tools["search_campus_activities"].ainvoke({"keyword": "活动", "limit": 20})
        )
