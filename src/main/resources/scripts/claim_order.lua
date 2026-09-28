-- Consumer side of the race with release_voucher.lua: only one of them can move an order off RESERVED.
local state = redis.call('GET', KEYS[1])
if state == 'PUBLISH_FAILED' then return 0 end
if state == 'RESERVED' or state == 'QUEUED' then
  redis.call('SET', KEYS[1], 'PROCESSING', 'EX', 3600)
end
return 1
