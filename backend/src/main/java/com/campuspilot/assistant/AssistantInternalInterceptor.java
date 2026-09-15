package com.campuspilot.assistant;

import cn.hutool.core.util.StrUtil;
import com.campuspilot.dto.UserDTO;
import com.campuspilot.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** 验证 Python 服务身份，并从签名上下文中恢复用户。 */
public class AssistantInternalInterceptor implements HandlerInterceptor {

    public static final String INTERNAL_TOKEN_HEADER = "X-Assistant-Internal-Token";
    public static final String USER_CONTEXT_HEADER = "X-Assistant-User-Context";

    private final String expectedToken;
    private final AssistantUserContextService userContextService;

    public AssistantInternalInterceptor(
            String expectedToken, AssistantUserContextService userContextService) {
        this.expectedToken = expectedToken;
        this.userContextService = userContextService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String actualToken = request.getHeader(INTERNAL_TOKEN_HEADER);
        if (StrUtil.isBlank(expectedToken) || !secureEquals(expectedToken, actualToken)) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "内部助手认证失败");
            return false;
        }

        try {
            UserDTO user = userContextService.verify(request.getHeader(USER_CONTEXT_HEADER));
            UserHolder.saveUser(user);
            return true;
        } catch (RuntimeException e) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "助手用户上下文无效");
            return false;
        }
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserHolder.removeUser();
    }

    private boolean secureEquals(String expected, String actual) {
        if (actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private void writeError(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"success\":false,\"errorMsg\":\"" + message + "\"}");
    }
}
