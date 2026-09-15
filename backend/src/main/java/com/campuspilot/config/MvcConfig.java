package com.campuspilot.config;

import com.campuspilot.assistant.AssistantInternalInterceptor;
import com.campuspilot.assistant.AssistantProperties;
import com.campuspilot.assistant.AssistantUserContextService;
import com.campuspilot.utils.LoginInterceptor;
import com.campuspilot.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/** 注册身份刷新、登录校验和助手内部认证拦截器。 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    private static final String INTERNAL_ASSISTANT_PATH = "/internal/assistant/**";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private AssistantProperties assistantProperties;

    @Resource
    private AssistantUserContextService assistantUserContextService;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AssistantInternalInterceptor(
                        assistantProperties.getInternalToken(), assistantUserContextService))
                .addPathPatterns(INTERNAL_ASSISTANT_PATH)
                .order(-1);

        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/user/code", "/user/login", "/error",
                        "/actuator/health", INTERNAL_ASSISTANT_PATH)
                .order(1);

        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
                .addPathPatterns("/**")
                .order(-2);
    }
}
