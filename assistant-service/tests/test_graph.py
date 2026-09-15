from app.graph import build_rule_plan


def test_free_basketball_plan() -> None:
    plan = build_rule_plan("帮我找免费的篮球活动")
    assert plan.intent == "activity_search"
    assert plan.type_id == 2
    assert plan.max_price == 0


def test_this_week_plan_ignores_question_words() -> None:
    plan = build_rule_plan("本周有什么活动？")
    assert plan.intent == "activity_search"
    assert plan.keyword == ""
    assert plan.start_time is not None
    assert plan.end_time is not None


def test_registration_query_plan() -> None:
    plan = build_rule_plan("我报名了什么？")
    assert plan.intent == "registration_query"


def test_registration_create_plan() -> None:
    plan = build_rule_plan("帮我报名新生篮球友谊赛")
    assert plan.intent == "registration_create"
    assert "新生篮球友谊赛" in plan.keyword


def test_registration_cancel_plan() -> None:
    plan = build_rule_plan("取消新生篮球友谊赛报名")
    assert plan.intent == "registration_cancel"
    assert "新生篮球友谊赛" in plan.keyword


def test_registration_help_plan() -> None:
    plan = build_rule_plan("怎么报名活动？")
    assert plan.intent == "platform_help"


def test_platform_help_fallback() -> None:
    plan = build_rule_plan("你会做什么？")
    assert plan.intent == "platform_help"
