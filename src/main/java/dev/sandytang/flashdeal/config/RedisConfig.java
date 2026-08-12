package dev.sandytang.flashdeal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Configuration
public class RedisConfig {
    @Bean
    DefaultRedisScript<Long> reserveVoucherScript() {
        var script = new DefaultRedisScript<Long>();
        script.setLocation(new ClassPathResource("scripts/reserve_voucher.lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Bean
    DefaultRedisScript<Long> releaseVoucherScript() {
        var script = new DefaultRedisScript<Long>();
        script.setLocation(new ClassPathResource("scripts/release_voucher.lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Bean
    DefaultRedisScript<Long> markVoucherQueuedScript() {
        var script = new DefaultRedisScript<Long>();
        script.setLocation(new ClassPathResource("scripts/mark_queued.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
