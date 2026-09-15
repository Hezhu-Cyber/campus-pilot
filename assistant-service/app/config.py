from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict

INSECURE_DEFAULT_TOKEN = "campus-pilot-local-assistant-token"
INSECURE_PLACEHOLDER_PREFIXES = ("replace-", "change-me", "your-")


class Settings(BaseSettings):
    """Runtime configuration loaded from environment variables."""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_prefix="ASSISTANT_",
        extra="ignore",
    )

    java_base_url: str = "http://127.0.0.1:8081"
    internal_token: str = ""
    request_timeout_seconds: float = 8.0

    model_base_url: str = ""
    model_api_key: str = ""
    model_name: str = ""
    model_temperature: float = 0.2
    model_timeout_seconds: float = 12.0

    assistant_timeout_seconds: float = 30.0
    agent_max_tool_rounds: int = 3
    history_max_messages: int = 12
    tool_result_max_chars: int = 16000

    @property
    def model_enabled(self) -> bool:
        """Return whether an OpenAI-compatible model is configured."""
        return bool(self.model_name and (self.model_api_key or self.model_base_url))

    def validate_runtime(self) -> None:
        """Fail closed when production-critical secrets are missing."""
        if (
            not self.internal_token
            or self.internal_token == INSECURE_DEFAULT_TOKEN
            or self.internal_token.lower().startswith(INSECURE_PLACEHOLDER_PREFIXES)
        ):
            raise RuntimeError(
                "ASSISTANT_INTERNAL_TOKEN must be set to a strong non-default value"
            )
        if self.request_timeout_seconds <= 0 or self.model_timeout_seconds <= 0:
            raise RuntimeError("assistant timeout values must be positive")
        if self.assistant_timeout_seconds <= self.model_timeout_seconds:
            raise RuntimeError(
                "ASSISTANT_ASSISTANT_TIMEOUT_SECONDS must exceed the single model timeout"
            )


@lru_cache
def get_settings() -> Settings:
    """Return the process-wide settings instance."""
    return Settings()
