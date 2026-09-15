package com.campuspilot.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** 组合时间戳和 Redis 自增序列生成全局唯一 ID。 */
@Component
public class RedisIdWorker {
    private StringRedisTemplate stringRedisTemplate;

    /** 初始化 RedisIdWorker。 */
    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /** 分布式 ID 的自定义起始时间（2022-01-01 UTC）。 */
    private static final  long BEGIN_TIMESTAMP=1640995200L;

    /** 分布式 ID 中留给每日序列号的位数。 */
    private static final  int COUNT_BITS=32;

    /** 生成带业务前缀隔离的 64 位全局唯一 ID。 */
    public long nextId(String keyPrefix)
    {

        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp=nowSecond-BEGIN_TIMESTAMP;

        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));

        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        return timestamp<<COUNT_BITS |count;

    }

}
