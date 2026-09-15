package com.campuspilot.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import cn.hutool.core.util.StrUtil;

/** 根据 Redis 配置创建 Redisson 分布式锁客户端。 */
@Configuration
public class RedissonConfig {
    @Value("${spring.redis.host:127.0.0.1}")
    private String host;
    @Value("${spring.redis.port:6379}")
    private int port;
    @Value("${spring.redis.password:}")
    private String password;

    /** 创建单节点 Redisson 客户端，密码为空时不发送 AUTH。 */
    @Bean
    public RedissonClient redissonClient(){

        org.redisson.config.Config config= new Config();
        org.redisson.config.SingleServerConfig server = config.useSingleServer()
                .setAddress("redis://" + host + ":" + port);
        if (StrUtil.isNotBlank(password)) {
            server.setPassword(password);
        }

        return Redisson.create(config);
    }

}
