package com.campuspilot.assistant;

import cn.hutool.core.util.StrUtil;
import com.campuspilot.dto.UserDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/** 签发并验证 Java 到 Python 的短期用户上下文。 */
@Service
public class AssistantUserContextService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @Resource
    private AssistantProperties properties;

    @Resource
    private ObjectMapper objectMapper;

    /** 为一次助手请求签发短期上下文。 */
    public String issue(UserDTO user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("assistant user context requires a user id");
        }
        long now = System.currentTimeMillis() / 1000L;
        TokenPayload payload = new TokenPayload();
        payload.userId = user.getId();
        payload.role = StrUtil.blankToDefault(user.getRole(), "STUDENT");
        payload.issuedAt = now;
        payload.expiresAt = now + properties.getUserContextTtlSeconds();
        try {
            String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(payload));
            return encodedPayload + "." + sign(encodedPayload);
        } catch (Exception e) {
            throw new IllegalStateException("unable to issue assistant user context", e);
        }
    }

    /** 验证签名、有效期并恢复最小用户上下文。 */
    public UserDTO verify(String token) {
        if (StrUtil.isBlank(token)) {
            throw new IllegalArgumentException("missing assistant user context");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            throw new IllegalArgumentException("invalid assistant user context");
        }
        byte[] expected;
        byte[] actual;
        try {
            expected = sign(parts[0]).getBytes(StandardCharsets.UTF_8);
            actual = parts[1].getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid assistant user context", e);
        }
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new IllegalArgumentException("invalid assistant user context signature");
        }

        TokenPayload payload;
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(parts[0]);
            payload = objectMapper.readValue(decoded, TokenPayload.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid assistant user context payload", e);
        }
        long now = System.currentTimeMillis() / 1000L;
        if (payload.userId == null || payload.expiresAt <= now || payload.issuedAt > now + 30) {
            throw new IllegalArgumentException("assistant user context expired");
        }

        UserDTO user = new UserDTO();
        user.setId(payload.userId);
        user.setRole(StrUtil.blankToDefault(payload.role, "STUDENT"));
        return user;
    }

    private String sign(String encodedPayload) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(
                properties.getUserContextSecret().getBytes(StandardCharsets.UTF_8),
                HMAC_ALGORITHM));
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8)));
    }

    /** 上下文载荷，只包含业务所需的最小字段。 */
    public static class TokenPayload {
        public Long userId;
        public String role;
        public long issuedAt;
        public long expiresAt;
    }
}
