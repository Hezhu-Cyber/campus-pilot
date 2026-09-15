-- 在一次 Redis 原子调用中切换点赞状态：1=新增点赞，-1=取消点赞。
local key = KEYS[1]
local userId = ARGV[1]
local score = ARGV[2]
if redis.call('zscore', key, userId) then
    -- 成员存在说明已点赞，再次点击即取消。
    redis.call('zrem', key, userId)
    return -1
end
-- 以时间戳作为分值，便于按点赞时间取用户列表。
redis.call('zadd', key, score, userId)
return 1
