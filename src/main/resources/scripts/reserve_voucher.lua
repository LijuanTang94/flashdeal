-- KEYS: stock, buyers-set, token, order-state
-- ARGV: user-id, token-value, order-id
if redis.call('GET', KEYS[3]) ~= ARGV[2] then return 3 end
if tonumber(redis.call('GET', KEYS[1]) or '-1') <= 0 then return 1 end
if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then return 2 end
redis.call('DECR', KEYS[1])
redis.call('SADD', KEYS[2], ARGV[1])
redis.call('DEL', KEYS[3])
redis.call('SET', KEYS[4], 'RESERVED', 'EX', 3600)
return 0
