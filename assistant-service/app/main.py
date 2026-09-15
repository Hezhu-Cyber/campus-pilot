import asyncio
import logging
import secrets
import time

from fastapi import Depends, FastAPI, Header, HTTPException, status
from prometheus_client import make_asgi_app

from .config import get_settings
from .graph import AssistantGraphService
from .java_client import JavaToolError
from .metrics import CHAT_LATENCY, CHAT_REQUESTS
from .schemas import ChatRequest, ChatResponse

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

settings = get_settings()
settings.validate_runtime()
assistant_service = AssistantGraphService(settings)
app = FastAPI(title="CampusPilot Assistant", version="0.3.0")
app.mount("/metrics", make_asgi_app())


async def verify_internal_token(
    token: str | None = Header(default=None, alias="X-Assistant-Internal-Token"),
) -> None:
    """Only allow requests originating from the Spring assistant gateway."""
    expected = settings.internal_token
    if not expected or not token or not secrets.compare_digest(expected, token):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="invalid internal token",
        )


@app.get("/health")
async def health() -> dict[str, str]:
    """Expose lightweight service status to the Spring gateway."""
    return {
        "status": "UP",
        "mode": "llm" if settings.model_enabled else "rules",
    }


@app.post(
    "/v1/assistant/chat",
    response_model=ChatResponse,
    dependencies=[Depends(verify_internal_token)],
)
async def chat(request: ChatRequest) -> ChatResponse:
    """Run one user message through the bounded assistant workflow."""
    started = time.perf_counter()
    outcome = "error"
    mode = "unknown"
    try:
        async with asyncio.timeout(settings.assistant_timeout_seconds):
            response = await assistant_service.chat(request)
            outcome = "success"
            mode = response.mode
            return response
    except TimeoutError as exc:
        outcome = "timeout"
        logger.warning("assistant request timed out")
        raise HTTPException(
            status_code=status.HTTP_504_GATEWAY_TIMEOUT,
            detail="智能助手处理超时，请稍后重试",
        ) from exc
    except JavaToolError as exc:
        outcome = "tool_unavailable"
        logger.warning("business tool unavailable: %s", exc)
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail="校园业务数据暂时不可用",
        ) from exc
    except Exception as exc:
        outcome = "internal_error"
        logger.exception("assistant request failed")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="智能助手处理失败",
        ) from exc
    finally:
        CHAT_REQUESTS.labels(outcome=outcome, mode=mode).inc()
        CHAT_LATENCY.labels(outcome=outcome, mode=mode).observe(
            time.perf_counter() - started
        )
