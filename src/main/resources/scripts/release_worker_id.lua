-- Free the worker id on shutdown, but only if this instance still owns it.
if redis.call('GET', KEYS[1]) == ARGV[1] then
  return redis.call('DEL', KEYS[1])
end
return 0
