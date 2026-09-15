local state = redis.call('get', KEYS[3])
if state == 'SUCCESS' then
    return 2
end
if state == 'COMPENSATED' then
    return 0
end
if not state then
    return 3
end

local removed = redis.call('srem', KEYS[2], ARGV[1])
if removed == 1 then
    redis.call('incr', KEYS[1])
end
redis.call('set', KEYS[3], 'COMPENSATED', 'EX', ARGV[2])
return 0
