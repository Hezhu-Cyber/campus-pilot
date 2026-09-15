package com.campuspilot.assistant;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/** 为助手网关单独配置 HTTP 客户端，避免影响其他远程调用。 */
@Configuration
public class AssistantHttpConfig {

    /** 创建带连接和读取超时的 RestTemplate。 */
    @Bean("assistantRestTemplate")
    public RestTemplate assistantRestTemplate(AssistantProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMillis());
        factory.setReadTimeout(properties.getReadTimeoutMillis());
        return new RestTemplate(factory);
    }
}
