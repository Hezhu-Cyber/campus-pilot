package com.campuspilot.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.campuspilot.utils.RedisConstants.CACHE_NULL_TTL;

/** 封装 Redis 缓存的通用读写逻辑。 */
@Component
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;

    /** 使用项目共用的 Redis 客户端创建缓存工具。 */
    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /** 将对象序列化为 JSON，并按指定时长写入 Redis。 */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 优先读取缓存，未命中时回源数据库。
     * 数据不存在时会短暂缓存空字符串，避免同一无效 ID 反复穿透到数据库。
     * 正缓存 TTL 附加 0~10% 的随机抖动，避免大量同 TTL 的键同时过期引发缓存雪崩。
     */
    public <R, ID> R queryWithPassThrough(
            String keyPrefix,
            ID id,
            Class<R> type,
            Function<ID, R> dbFallback,
            Long time,
            TimeUnit unit) {
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        // 空字符串是已缓存的"数据不存在"标记；null 才代表未命中。
        if (json != null) {
            return null;
        }

        R value = dbFallback.apply(id);
        if (value == null) {
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        set(key, value, jitteredTtlMillis(time, unit), TimeUnit.MILLISECONDS);
        return value;
    }

    /** 在原始 TTL 上附加 0~10% 的随机抖动（毫秒）。 */
    private long jitteredTtlMillis(Long time, TimeUnit unit) {
        long ttlMillis = Math.max(1L, unit.toMillis(time));
        long jitter = ThreadLocalRandom.current().nextLong(Math.max(1L, ttlMillis / 10L));
        return ttlMillis + jitter;
    }
}
