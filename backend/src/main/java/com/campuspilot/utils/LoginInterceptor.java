package com.campuspilot.utils;

import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;

/** 对受保护请求校验当前线程中的登录用户。 */
public class LoginInterceptor implements HandlerInterceptor {

    private static final List<String> PUBLIC_GET_PREFIXES = Arrays.asList(
            "/registration-pass/activity/", "/post-comments/post/"
    );

    /** 放行公开 GET 请求；其余请求在未登录时直接返回 401。 */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("GET".equalsIgnoreCase(request.getMethod()) && isPublicGet(request.getRequestURI())) {
            return true;
        }
        if (UserHolder.getUser() == null) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"errorMsg\":\"请先登录\"}");
            return false;
        }

        return true;
    }

    /** 判断 GET 请求是否命中允许匿名访问的路径前缀。 */
    private boolean isPublicGet(String uri) {
        if ("/activity-category/list".equals(uri) || "/activity/search".equals(uri)
                || "/activity/by-category".equals(uri) || "/post/hot".equals(uri)
                || "/post/by-activity".equals(uri)) {
            return true;
        }
        if (uri.matches("/activity/\\d+") || uri.matches("/post/\\d+")
                || uri.matches("/post/likes/\\d+") || uri.matches("/user/\\d+")
                || uri.matches("/user/info/\\d+")) {
            return true;
        }
        return PUBLIC_GET_PREFIXES.stream()
                .anyMatch(prefix -> prefix.endsWith("/") && uri.startsWith(prefix));
    }
}
