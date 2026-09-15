-- 1. 从 Redis 读出：库存、开始时间、结束时间、是否启用
local stock = redis.call('get', KEYS[1])
local beginTime = redis.call('get', KEYS[3])
local endTime = redis.call('get', KEYS[4])
local enabled = redis.call('get', KEYS[5])

-- 2. 配置不齐 → 返回 5（活动不存在或未启用）
if (not stock) or (not beginTime) or (not endTime) or enabled ~= '1' then
    return 5
end
-- 3. 如果这个报名已经处理过（RESERVED/PROCESSING/SUCCESS）→ 返回 0（当作成功，幂等）
local existingState = redis.call('get', KEYS[6])
if existingState == 'RESERVED' or existingState == 'PROCESSING' or existingState == 'SUCCESS' then
    return 0
end
-- 4. 时间检查：没开始返回 3，已结束返回 4
local now = tonumber(ARGV[2])
if now < tonumber(beginTime) then
    return 3
end
if now > tonumber(endTime) then
    return 4
end
-- 5. 没库存 → 返回 1（名额已满）
if tonumber(stock) <= 0 then
    return 1
end
-- 6. 你已经抢过 → 返回 2（不能重复报名）
if redis.call('sismember', KEYS[2], ARGV[1]) == 1 then
    return 2
end

-- 7. 一切通过：扣 1 个名额、把你的 ID 记进"已抢集合"、标记"已占座"
redis.call('decr', KEYS[1])
redis.call('sadd', KEYS[2], ARGV[1])
redis.call('set', KEYS[6], 'RESERVED', 'EX', ARGV[3])
return 0
