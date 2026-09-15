from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


class ApiModel(BaseModel):
    """Base model that exposes camelCase fields to Java and Vue."""

    model_config = ConfigDict(
        alias_generator=to_camel,
        populate_by_name=True,
        extra="ignore",
    )


class ConversationMessage(ApiModel):
    """A compact, server-managed conversation message."""

    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=4000)


class ChatRequest(ApiModel):
    """One assistant turn forwarded by the Spring gateway."""

    thread_id: str = Field(min_length=1, max_length=128)
    message: str = Field(min_length=1, max_length=1000)
    page_path: str = Field(default="", max_length=256)
    user_context_token: str = Field(min_length=20, max_length=4096)
    history: list[ConversationMessage] = Field(default_factory=list, max_length=20)


class AssistantPlan(ApiModel):
    """Structured request plan produced by the deterministic fallback."""

    intent: Literal[
        "activity_search",
        "registration_query",
        "post_query",
        "registration_create",
        "registration_cancel",
        "platform_help",
    ] = "platform_help"
    keyword: str = ""
    type_id: int | None = None
    max_price: int | None = None
    start_time: str | None = None
    end_time: str | None = None
    limit: int = Field(default=5, ge=1, le=10)
    activity_id: str | None = None
    registration_id: str | None = None


class ActionIntentPlan(ApiModel):
    """LLM-assisted classification for an explicit write intent."""

    intent: Literal["none", "registration_create", "registration_cancel"] = "none"
    keyword: str = Field(default="", max_length=128)
    explicit: bool = False
    confidence: float = Field(default=0.0, ge=0.0, le=1.0)


class ChatResponse(ApiModel):
    """Structured answer consumed by the Spring gateway."""

    thread_id: str
    answer: str
    activities: list[dict[str, Any]] = Field(default_factory=list)
    registrations: list[dict[str, Any]] = Field(default_factory=list)
    posts: list[dict[str, Any]] = Field(default_factory=list)
    suggested_questions: list[str] = Field(default_factory=list)
    pending_action: dict[str, Any] | None = None
    mode: Literal["llm", "rules", "degraded"] = "rules"
