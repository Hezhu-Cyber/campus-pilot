"""Deterministic answers used by rule mode and safety fallbacks."""

from __future__ import annotations

from typing import Any

from .rules import faq_answer
from .schemas import AssistantPlan, ChatRequest


def build_deterministic_answer(request: ChatRequest, plan: AssistantPlan, context: dict[str, Any]) -> str:
    activities = context.get("activities") or []
    registrations = context.get("registrations") or []
    posts = context.get("posts") or []

    if plan.intent in ("registration_create", "registration_cancel"):
        pending = context.get("pendingAction")
        if pending:
            return f"已准备好：{pending.get('summary', '待确认操作')}。请点击下方按钮确认，确认后才会真正执行。"
        return context.get("actionError") or "暂时无法准备该操作，请换一种说法再试。"
    if plan.intent == "activity_search":
        if not activities:
            return "暂时没有找到符合条件的活动。你可以换个分类、时间或关键词再试试。"
        lines = [f"我找到了 {len(activities)} 个活动："]
        for index, item in enumerate(activities, start=1):
            price = item.get("avgPrice")
            price_text = "免费" if str(price) in ("0", "None") else f"参考费用 {price} 元"
            when = item.get("startTime") or item.get("openHours") or "时间待定"
            lines.append(f"{index}. {item.get('name', '校园活动')}｜{when}｜{item.get('address') or item.get('area') or '地点待定'}｜{price_text}")
        return "\n".join(lines)
    if plan.intent == "registration_query":
        if not registrations:
            return "你目前还没有报名记录。可以问我“推荐一些本周活动”，我再帮你挑选。"
        status_names = {1: "已报名", 2: "已完成", 4: "已取消"}
        lines = [f"你共有 {len(registrations)} 条报名记录："]
        for index, item in enumerate(registrations, start=1):
            try:
                status_value = int(item.get("status"))
            except (TypeError, ValueError):
                status_value = -1
            status = status_names.get(status_value, "处理中")
            lines.append(f"{index}. {item.get('activityName') or item.get('title') or '校园活动'}｜{status}｜{item.get('openHours') or item.get('address') or ''}".rstrip("｜"))
        return "\n".join(lines)
    if plan.intent == "post_query":
        if not posts:
            return "目前还没有热门校园动态。"
        return "\n".join([f"当前有 {len(posts)} 条热门动态：", *[f"{index}. {item.get('title', '校园动态')}｜{item.get('liked', 0)} 个赞｜{item.get('comments', 0)} 条评论" for index, item in enumerate(posts, start=1)]])
    return faq_answer(request.message) or "同学，你好。我可以帮你找活动、按时间和分类推荐活动、查看你的报名记录，也可以介绍热门动态。对于报名和取消报名，我会先生成确认卡，只有你点击确认后才会调用现有报名流程。"
