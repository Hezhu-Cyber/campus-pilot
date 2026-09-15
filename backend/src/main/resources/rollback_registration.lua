-- 当数据库落库失败或用户取消时，原子移除预留并回补库存。
local registrationPassId = ARGV[1]
local userId = ARGV[2]
local registrationId = ARGV[3]
local stockKey = 'registration:{' .. registrationPassId .. '}:stock'
local userKey = 'registration:{' .. registrationPassId .. '}:users'
local transactionKey = 'registration:{' .. registrationPassId .. '}:tx:' .. registrationId
-- 取消成功后必须清除旧 SUCCESS 状态，否则再次报名会被误判为已预占。
redis.call('del', transactionKey)
-- 只有用户确实在预留集合中时才加回库存，避免重复补偿。
if redis.call('srem', userKey, userId) == 1 then
    redis.call('incrby', stockKey, 1)
    return 1
end
return 0
