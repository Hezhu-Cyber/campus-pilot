from prometheus_client import Counter, Histogram

CHAT_REQUESTS = Counter(
    "assistant_chat_requests_total",
    "Assistant chat requests by outcome and mode.",
    ("outcome", "mode"),
)
CHAT_LATENCY = Histogram(
    "assistant_chat_latency_seconds",
    "Assistant chat latency in seconds.",
    ("outcome", "mode"),
    buckets=(0.05, 0.1, 0.25, 0.5, 1, 2, 5, 10, 20, 30, 45),
)
TOOL_CALLS = Counter(
    "assistant_tool_calls_total",
    "Assistant tool calls by tool and outcome.",
    ("tool", "outcome"),
)
AGENT_FALLBACKS = Counter(
    "assistant_agent_fallbacks_total",
    "Assistant agent fallbacks by reason.",
    ("reason",),
)
