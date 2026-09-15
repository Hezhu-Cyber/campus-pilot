package com.campuspilot.assistant;

import cn.hutool.core.util.StrUtil;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/** 智能助手网关配置。 */
@Data
@Component
@ConfigurationProperties(prefix = "app.assistant")
public class AssistantProperties {

    private static final String INSECURE_DEFAULT_TOKEN = "campus-pilot-local-assistant-token";
    private static final int MIN_SECRET_LENGTH = 32;

    /** 是否启用智能助手入口。 */
    private boolean enabled = false;

    /** Python FastAPI 助手服务地址。 */
    private String pythonBaseUrl = "http://127.0.0.1:8011";

    /** Java 与 Python 服务之间共享的内部认证令牌。 */
    private String internalToken = "";

    /** 仅 Java 持有，用于签发短期用户上下文，Python 只透传。 */
    private String userContextSecret = "";

    /** 用户上下文有效期。 */
    private int userContextTtlSeconds = 60;

    /** 连接 Python 服务的超时时间。 */
    private int connectTimeoutMillis = 2000;

    /** 等待模型回答的超时时间。 */
    private int readTimeoutMillis = 35000;

    /** 每轮请求最多回传的历史消息数。 */
    private int historyMaxMessages = 12;

    /** 会话历史保留天数。 */
    private int historyTtlDays = 7;

    /** 单个用户每分钟最多发起的助手请求数。 */
    private int rateLimitPerMinute = 20;

    /** 启动时拒绝默认密钥或不安全配置，避免生产环境裸奔。 */
    @PostConstruct
    public void validateRuntime() {
        if (!enabled) {
            return;
        }
        if (StrUtil.isBlank(internalToken)
                || INSECURE_DEFAULT_TOKEN.equals(internalToken)
                || isPlaceholder(internalToken)
                || internalToken.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "ASSISTANT_INTERNAL_TOKEN must be a non-default secret of at least 32 characters");
        }
        if (StrUtil.isBlank(userContextSecret)
                || INSECURE_DEFAULT_TOKEN.equals(userContextSecret)
                || isPlaceholder(userContextSecret)
                || userContextSecret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "ASSISTANT_USER_CONTEXT_SECRET must be a non-default secret of at least 32 characters");
        }
        if (userContextTtlSeconds < 15 || userContextTtlSeconds > 300) {
            throw new IllegalStateException("assistant user context TTL must be between 15 and 300 seconds");
        }
        if (historyMaxMessages < 2 || historyMaxMessages > 50 || historyTtlDays < 1) {
            throw new IllegalStateException("assistant history settings are invalid");
        }
        if (rateLimitPerMinute < 0 || rateLimitPerMinute > 1000) {
            throw new IllegalStateException("assistant rate limit must be between 0 and 1000");
        }
    }

    private boolean isPlaceholder(String value) {
        String normalized = value == null ? "" : value.toLowerCase();
        return normalized.startsWith("replace-")
                || normalized.startsWith("change-me")
                || normalized.startsWith("your-");
    }
}
