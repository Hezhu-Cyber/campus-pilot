# Assistant service module map

- `main.py`: FastAPI entry point, internal authentication, timeout mapping, and metrics endpoint.
- `graph.py`: request orchestration and LangGraph node wiring. It owns no routing vocabulary or answer copy.
- `rules.py`: deterministic intent routing, date/category extraction, FAQ copy, and suggested questions.
- `safety.py`: prompt-injection checks, PII redaction, tool-result sanitization, and output guards.
- `answers.py`: deterministic response rendering for rules mode and LLM fallbacks.
- `tools.py`: request-scoped LangChain tool definitions.
- `java_client.py`: authenticated client for Spring's internal assistant APIs.
- `schemas.py`: request, response, and planning contracts shared across the service.

Keep business writes out of the LLM tool set. Registration and cancellation are only prepared here; Java owns confirmation and execution.
