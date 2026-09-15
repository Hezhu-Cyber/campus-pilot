package com.campuspilot;

import com.campuspilot.utils.LoginInterceptor;
import com.campuspilot.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 验证匿名用户在公开读接口和受保护写接口上的权限差异。 */
class LoginInterceptorTests {
    private final LoginInterceptor interceptor = new LoginInterceptor();

    /** 每个测试后清理线程中的用户，避免测试互相污染。 */
    @AfterEach
    void cleanup() { UserHolder.removeUser(); }

    /** 验证匿名用户可以访问公开的活动详情。 */
    @Test
    void anonymousUserCanReadActivity() throws Exception {
        HttpServletRequest request = request("GET", "/activity/4");
        assertTrue(interceptor.preHandle(request, mock(HttpServletResponse.class), new Object()));
    }

    /** 验证匿名用户创建活动时会收到 401。 */
    @Test
    void anonymousUserCannotCreateActivity() throws Exception {
        HttpServletRequest request = request("POST", "/activity");
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(new java.io.PrintWriter(new java.io.StringWriter()));
        assertFalse(interceptor.preHandle(request, response, new Object()));
        verify(response).setStatus(401);
    }

    /** 创建带指定 HTTP 方法和 URI 的模拟请求。 */
    private HttpServletRequest request(String method, String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getRequestURI()).thenReturn(uri);
        return request;
    }
}
