"""Deterministic intent routing and fixed assistant copy."""

from __future__ import annotations

import re
from datetime import datetime, timedelta, timezone
from typing import Any, Literal

from .schemas import AssistantPlan

CHINA_TZ = timezone(timedelta(hours=8))

#将活动映射为活动分类数字
CATEGORY_IDS = {
    "学术讲座": 1,
    "讲座": 1,
    "体育": 2,
    "篮球": 2,
    "赛事": 2,
    "社团": 3,
    "文艺": 4,
    "演出": 4,
    "志愿": 5,
    "创新": 6,
    "创业": 6,
    "竞赛": 7,
    "比赛": 7,
    "休闲": 8,
    "娱乐": 8,
    "场馆": 9,
}


def build_rule_plan(message: str) -> AssistantPlan:
    """Build a deterministic plan from a user message."""
    text = message.strip()
    intent: Literal[
        "activity_search", "registration_query", "post_query", "registration_create",
        "registration_cancel", "platform_help"
    ] = "platform_help"

    if any(word in text for word in (
        "怎么报名", "如何报名", "报名流程", "报名方式",
        "怎么取消", "如何取消", "取消流程",
    )):
        intent = "platform_help"
    elif (
        any(word in text for word in ("取消", "退订", "退掉", "退出", "不去了"))
        and any(word in text for word in ("报名", "预约", "名额", "活动"))
    ) or "取消报名" in text or "取消预约" in text:
        intent = "registration_cancel"
    elif any(word in text for word in ("帮我报名", "我要报名", "替我报名", "报名这个", "报名活动", "预约活动", "登记报名")):
        intent = "registration_create"
    elif any(word in text for word in ("我的报名", "报名记录", "报过什么", "我报了", "报名了什么")):
        intent = "registration_query"
    elif any(word in text for word in ("热门动态", "帖子", "动态")) and "发布" not in text:
        intent = "post_query"
    elif any(word in text for word in (
        "活动", "讲座", "篮球", "体育", "社团", "演出", "志愿", "创新", "创业",
        "竞赛", "比赛", "周末", "场馆", "推荐", "找",
    )):
        intent = "activity_search"

    type_id = next((value for key, value in CATEGORY_IDS.items() if key in text), None)
    max_price = 0 if "免费" in text else None
    price_match = re.search(r"(?:不超过|低于|最多)\s*(\d+)\s*元?", text)
    if price_match:
        max_price = int(price_match.group(1))

    start_time: str | None = None
    end_time: str | None = None
    if intent == "activity_search":
        start_time, end_time = _date_bounds(text)

    keyword = ""
    if intent == "activity_search":
        keyword = re.sub(
            r"(帮我|我想|想找|找一下|找|有没有|有什么|有哪些|哪些|什么|推荐|一下|校园|活动|今天|明天|这周|本周|周末|免费|还有|呢|吗|的)",
            " ", text,
        )
        keyword = re.sub(r"\s+", " ", keyword).strip(" ，。！？,.!?")
    elif intent == "registration_create":
        keyword = _extract_action_keyword(text, cancel=False)
    elif intent == "registration_cancel":
        keyword = _extract_action_keyword(text, cancel=True)

    if keyword in CATEGORY_IDS:
        keyword = ""

    return AssistantPlan(
        intent=intent, keyword=keyword, type_id=type_id, max_price=max_price,
        start_time=start_time, end_time=end_time, limit=5,
    )


def _extract_action_keyword(text: str, cancel: bool) -> str:
    pattern = (
        r"(帮我|我要|替我|请|帮忙|取消|退订|退掉|退出|预约|报名|这个|一下|活动|的名额|的)"
        if cancel else r"(帮我|我要|替我|请|帮忙|报名|预约|登记|这个|一下|活动|的名额|的)"
    )
    keyword = re.sub(pattern, " ", text)
    keyword = re.sub(r"\s+", " ", keyword).strip(" ，。！？,.!?")
    return "" if len(keyword) < 2 else keyword


def _date_bounds(message: str) -> tuple[str | None, str | None]:
    now = datetime.now(CHINA_TZ).replace(tzinfo=None)
    if "今天" in message:
        start = now.replace(hour=0, minute=0, second=0, microsecond=0)
        end = start + timedelta(days=1) - timedelta(seconds=1)
    elif "明天" in message:
        start = (now + timedelta(days=1)).replace(hour=0, minute=0, second=0, microsecond=0)
        end = start + timedelta(days=1) - timedelta(seconds=1)
    elif "周末" in message:
        start = (now + timedelta(days=(5 - now.weekday()) % 7)).replace(hour=0, minute=0, second=0, microsecond=0)
        end = start + timedelta(days=1, hours=23, minutes=59, seconds=59)
    elif "本周" in message:
        start = now
        end = (now + timedelta(days=(6 - now.weekday()) % 7)).replace(hour=23, minute=59, second=59, microsecond=0)
    else:
        return None, None
    return start.isoformat(timespec="seconds"), end.isoformat(timespec="seconds")


def should_handoff(message: str) -> bool:
    if any(word in message for word in ("我的工单", "工单状态", "查工单", "工单进度")):
        return False
    return any(word in message for word in ("转人工", "人工客服", "联系客服", "投诉", "提交工单", "创建工单", "反馈问题"))


def support_category(message: str) -> str:
    if "投诉" in message:
        return "COMPLAINT"
    if any(word in message for word in ("报名", "活动", "名额")):
        return "REGISTRATION"
    if any(word in message for word in ("登录", "账号", "验证码", "密码")):
        return "ACCOUNT"
    return "OTHER"


def faq_answer(message: str) -> str | None:
    if any(word in message for word in ("怎么报名", "如何报名", "报名流程", "报名方式")):
        return "报名步骤：1. 打开活动详情；2. 选择活动提供的报名方式；3. 点击报名并按页面提示完成；4. 到“个人中心-我的报名”确认结果。你也可以告诉我活动完整名称，我会先生成确认卡。"
    if any(word in message for word in ("怎么取消", "如何取消", "取消流程")):
        return "取消报名：打开“个人中心-我的报名”，找到对应活动后点击取消；也可以直接告诉我完整活动名称，我会先生成取消确认卡，确认后才会执行。"
    if any(word in message for word in ("报名记录", "报名结果", "报名成功了吗")):
        return "你可以查看“个人中心-我的报名”，或直接问我“我的报名记录”。"
    return None


def suggested_questions_for(plan: dict[str, Any]) -> list[str]:
    intent = plan.get("intent", "platform_help")
    if intent == "activity_search":
        return ["给我推荐免费的活动", "本周还有哪些讲座？", "看看热门校园动态"]
    if intent == "registration_query":
        return ["推荐我可能感兴趣的活动", "哪些活动即将截止报名？", "查看热门动态"]
    if intent == "post_query":
        return ["最近有哪些活动？", "我的报名记录", "推荐周末活动"]
    return ["本周有什么活动？", "帮我报名新生篮球友谊赛", "取消报名", "我的报名记录"]
