package dev.sandytang.flashdeal.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

@Service
public class SeckillTokenService {
    private final StringRedisTemplate redis;
    private final Duration ttl;

    public SeckillTokenService(StringRedisTemplate redis,
            @Value("${flashdeal.token-ttl:PT10S}") Duration ttl) {
        this.redis = redis; this.ttl = ttl;
    }

    public Map<String, Object> issue(long userId, long voucherId) {
        String token = UUID.randomUUID().toString();
        redis.opsForValue().set(RedisKeys.token(voucherId, userId), token, ttl);
        return Map.of("token", token, "expiresInMs", ttl.toMillis());
    }
}
