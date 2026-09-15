import asyncio
from unittest.mock import patch

from app.config import Settings
from app.graph import AssistantGraphService
from app.schemas import ChatRequest


class FakeTool:
    def __init__(self, result):
        self.result = result

    async def ainvoke(self, payload=None):
        return self.result


def test_graph_activity_route_and_state_reset() -> None:
    service = AssistantGraphService(Settings(model_name="", model_api_key=""))
    fake_tools = {
        "search_campus_activities": FakeTool(
            [{"id": "2", "name": "新生篮球友谊赛", "avgPrice": "0"}]
        ),
        "list_my_registrations": FakeTool(
            [{"id": "10", "activityName": "人工智能前沿讲座", "status": 1}]
        ),
    }
    with patch("app.graph.build_langchain_tools", return_value=fake_tools):
        first = asyncio.run(
            service.chat(
                ChatRequest(
                    thread_id="graph-test",
                    user_context_token="signed-context-token-123456",
                    message="帮我找免费的篮球活动",
                )
            )
        )
        second = asyncio.run(
            service.chat(
                ChatRequest(
                    thread_id="graph-test",
                    user_context_token="signed-context-token-123456",
                    message="查看我的报名记录",
                )
            )
        )
    assert first.activities[0]["name"] == "新生篮球友谊赛"
    assert "新生篮球友谊赛" in first.answer
    assert second.activities == []
    assert "人工智能前沿讲座" in second.answer


def test_graph_prepares_registration_confirmation() -> None:
    service = AssistantGraphService(Settings(model_name="", model_api_key=""))
    fake_tools = {
        "search_campus_activities": FakeTool(
            [{"id": "2", "name": "新生篮球友谊赛"}]
        ),
        "list_registration_passes": FakeTool(
            [{"id": "20", "type": 0, "title": "篮球赛参赛名额"}]
        ),
        "prepare_registration_action": FakeTool(
            {
                "success": True,
                "data": {
                    "confirmationToken": "token-1",
                    "actionType": "register",
                    "summary": "确认报名「新生篮球友谊赛」",
                },
            }
        ),
    }
    with patch("app.graph.build_langchain_tools", return_value=fake_tools):
        response = asyncio.run(
            service.chat(
                ChatRequest(
                    thread_id="register-test",
                    user_context_token="signed-context-token-123456",
                    message="帮我报名新生篮球友谊赛",
                )
            )
        )
    assert response.pending_action["confirmationToken"] == "token-1"
    assert "点击下方按钮确认" in response.answer


def test_handoff_creates_support_ticket() -> None:
    service = AssistantGraphService(Settings(model_name="", model_api_key=""))

    class FakeJavaClient:
        def __init__(self, settings, user_context_token):
            self.user_context_token = user_context_token

        async def create_support_ticket(self, **kwargs):
            return {"success": True, "data": 99}

        async def close(self):
            return None

    with patch("app.graph.JavaToolClient", FakeJavaClient):
        response = asyncio.run(
            service.chat(
                ChatRequest(
                    thread_id="ticket-test",
                    user_context_token="signed-context-token-123456",
                    message="我要转人工投诉报名问题",
                )
            )
        )
    assert "工单编号 99" in response.answer
    assert response.mode == "rules"


def test_registration_help_does_not_create_action() -> None:
    service = AssistantGraphService(Settings(model_name="", model_api_key=""))
    response = asyncio.run(
        service.chat(
            ChatRequest(
                thread_id="help-test",
                user_context_token="signed-context-token-123456",
                message="怎么报名活动？",
            )
        )
    )
    assert response.pending_action is None
    assert "报名步骤" in response.answer
