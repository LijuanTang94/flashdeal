-- Keep this instance's worker-id lease alive; re-take it only if it expired and nobody else claimed it.
local owner = redis.call('GET', KEYS[1])
if owner == ARGV[1] then
  redis.call('PEXPIRE', KEYS[1], ARGV[2])
  return 1
end
if not owner then
  redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
  return 1
end
return 0
