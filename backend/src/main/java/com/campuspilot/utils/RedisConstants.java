package com.campuspilot.utils;
//Redis 里所有"钥匙"的命名和过期时间，全项目最浓缩的清单
/** 集中定义 Redis 键前缀与过期时间。 */
public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 120L;
    public static final String LOGIN_CODE_RATE_KEY = "login:code:rate:";
    public static final String LOGIN_CODE_ATTEMPT_KEY = "login:code:attempt:";
    public static final String LOGIN_PASSWORD_ATTEMPT_KEY = "login:password:attempt:";
    public static final int LOGIN_MAX_ATTEMPTS = 5;
    public static final long LOGIN_LOCK_MINUTES = 15L;

    public static final Long CACHE_NULL_TTL = 2L;

    public static final Long CACHE_ACTIVITY_TTL = 30L;
    public static final String CACHE_ACTIVITY_KEY = "cache:activity:";

    public static final String POST_LIKED_KEY = "post:liked:";
}
