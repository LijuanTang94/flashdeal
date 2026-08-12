-- Compensates only the reservation owned by this user/order after publish failure.
if redis.call('GET', KEYS[3]) ~= 'RESERVED' then return 0 end
if redis.call('SREM', KEYS[2], ARGV[1]) == 1 then
  redis.call('INCR', KEYS[1])
end
redis.call('SET', KEYS[3], 'PUBLISH_FAILED', 'EX', 3600)
return 1
