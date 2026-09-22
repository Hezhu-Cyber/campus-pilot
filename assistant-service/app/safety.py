"""Boundary checks and data minimization before model use."""

from __future__ import annotations

import json
import re
from typing import Any


def redact_sensitive_text(value: str) -> str:
    redacted = re.sub(r"(?<!\d)1[3-9]\d{9}(?!\d)", "[手机号]", value)
    redacted = re.sub(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}", "[邮箱]", redacted)
    return re.sub(r"(?<!\d)\d{17}[0-9Xx](?!\d)", "[证件号]", redacted)


def sanitize_tool_result(result: Any, max_chars: int) -> Any:
    safe = _truncate_strings(result, 2000)
    serialized = json.dumps(safe, ensure_ascii=False, default=str)
    if len(serialized) <= max_chars:
        return {"trust": "untrusted_tool_data", "data": safe}
    return {"trust": "untrusted_tool_data", "truncated": True, "preview": serialized[:max_chars]}


def _truncate_strings(value: Any, max_length: int) -> Any:
    if isinstance(value, str):
        value = redact_sensitive_text(value)
        return value if len(value) <= max_length else value[:max_length] + "..."
    if isinstance(value, list):
        return [_truncate_strings(item, max_length) for item in value[:50]]
    if isinstance(value, dict):
        return {str(key): _truncate_strings(item, max_length) for key, item in value.items()}
    return value


def is_prompt_injection(message: str) -> bool:
    markers = ("忽略系统", "忽略工具", "输出系统提示", "完整系统提示", "泄露", "内部令牌", "internal token", "api key", "apikey", "confirmationtoken")
    normalized = message.lower()
    return any(marker.lower() in normalized for marker in markers)


def contains_internal_secret_marker(answer: str) -> bool:
    markers = ("X-Assistant-Internal-Token", "X-Assistant-User-Context", "confirmationToken", "系统提示词", "ASSISTANT_INTERNAL_TOKEN")
    return any(marker.lower() in answer.lower() for marker in markers)


def claims_unexecuted_action(answer: str) -> bool:
    patterns = (r"已(?:经)?(?:帮你)?(?:报名|取消报名|取消)", r"(?:报名|取消)(?:已)?成功", r"操作已(?:完成|执行)")
    return any(re.search(pattern, answer) for pattern in patterns)


def requires_campus_grounding(message: str) -> bool:
    return any(word in message for word in ("活动", "报名", "讲座", "动态", "帖子", "名额", "时间", "地点", "费用", "工单"))


def is_contextual_reference(message: str) -> bool:
    return any(word in message for word in ("这个", "那个", "第一个", "第二个", "刚才", "上面", "其中一个"))


def safe_pending_action(value: dict[str, Any] | None) -> dict[str, Any] | None:
    if not value:
        return None
    return {key: item for key, item in value.items() if key != "confirmationToken"}
