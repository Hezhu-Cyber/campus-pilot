import asyncio
from unittest.mock import patch

from langchain_core.messages import AIMessage

from app.config import Settings
from app.graph import AssistantGraphService
from app.schemas import ChatRequest


class FakeTool:
    def __init__(self, result):
        self.result = result

    async def ainvoke(self, payload=None):
        return self.result


class FakeToolCallingModel:
    def __init__(self):
        self.calls = 0

    def bind_tools(self, tools):
        self.tools = tools
        return self

    async def ainvoke(self, messages):
        self.calls += 1
        if self.calls == 1:
            return AIMessage(
                content="",
                tool_calls=[{
                    "name": "search_campus_activities",
                    "args": {"keyword": "篮球", "limit": 5},
                    "id": "call-1",
                    "type": "tool_call",
                }],
            )
        return AIMessage(content="我通过工具找到了篮球活动。")


def test_llm_agent_executes_tool_and_returns_answer() -> None:
    service = AssistantGraphService(Settings(model_name="fake", model_api_key="test"))
    service._llm = FakeToolCallingModel()
    fake_tools = {
        "search_campus_activities": FakeTool(
            [{"id": "2", "name": "新生篮球友谊赛"}]
        )
    }
    with patch("app.graph.build_langchain_tools", return_value=fake_tools):
        response = asyncio.run(
            service.chat(
                ChatRequest(
                    thread_id="agent-test",
                    user_context_token="signed-context-token-123456",
                    message="找篮球活动",
                )
            )
        )
    assert response.mode == "llm"
    assert response.answer == "我通过工具找到了篮球活动。"
    assert response.activities[0]["name"] == "新生篮球友谊赛"
