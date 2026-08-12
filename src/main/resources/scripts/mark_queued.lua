-- Do not overwrite CREATED if a very fast consumer won the race.
if redis.call('GET', KEYS[1]) == 'RESERVED' then
  redis.call('SET', KEYS[1], 'QUEUED', 'EX', 3600)
  return 1
end
return 0
