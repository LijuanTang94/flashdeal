package dev.sandytang.flashdeal.service;

import dev.sandytang.flashdeal.persistence.SeckillVoucherRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class VoucherWarmup {
    private final SeckillVoucherRepository vouchers;
    private final StringRedisTemplate redis;
    public VoucherWarmup(SeckillVoucherRepository vouchers, StringRedisTemplate redis) {
        this.vouchers = vouchers; this.redis = redis;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warm() {
        vouchers.findAll().forEach(v -> redis.opsForValue().setIfAbsent(
                RedisKeys.stock(v.getId()), Integer.toString(v.getStock())));
    }
}
