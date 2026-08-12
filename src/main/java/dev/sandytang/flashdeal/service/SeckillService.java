package dev.sandytang.flashdeal.service;

import dev.sandytang.flashdeal.domain.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.List;

@Service
public class SeckillService {
    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> reserveScript;
    private final DefaultRedisScript<Long> releaseScript;
    private final DefaultRedisScript<Long> markQueuedScript;
    private final SnowflakeIdGenerator ids;
    private final OrderPublisher publisher;

    public SeckillService(StringRedisTemplate redis,
                          @Qualifier("reserveVoucherScript") DefaultRedisScript<Long> reserveVoucherScript,
                          @Qualifier("releaseVoucherScript") DefaultRedisScript<Long> releaseVoucherScript,
                          @Qualifier("markVoucherQueuedScript") DefaultRedisScript<Long> markVoucherQueuedScript,
                          SnowflakeIdGenerator ids, OrderPublisher publisher) {
        this.redis = redis; this.reserveScript = reserveVoucherScript;
        this.releaseScript = releaseVoucherScript; this.markQueuedScript = markVoucherQueuedScript;
        this.ids = ids; this.publisher = publisher;
    }

    public AcceptedOrder reserve(long userId, long voucherId, String token) {
        long orderId = ids.nextId();
        List<String> keys = List.of(RedisKeys.stock(voucherId), RedisKeys.buyers(voucherId),
                RedisKeys.token(voucherId, userId), RedisKeys.orderState(orderId));
        Long result = redis.execute(reserveScript, keys,
                Long.toString(userId), token, Long.toString(orderId));
        if (result == null) throw new IllegalStateException("Redis returned no result");
        if (result == 1) throw new SeckillRejectedException("SOLD_OUT", "Voucher is sold out", HttpStatus.CONFLICT);
        if (result == 2) throw new SeckillRejectedException("DUPLICATE_ORDER", "One order per user", HttpStatus.CONFLICT);
        if (result == 3) throw new SeckillRejectedException("INVALID_TOKEN", "Token is invalid or expired", HttpStatus.UNAUTHORIZED);

        try {
            publisher.publish(new OrderMessage(orderId, userId, voucherId, Instant.now()));
            redis.execute(markQueuedScript, List.of(RedisKeys.orderState(orderId)));
            return new AcceptedOrder(orderId, "QUEUED");
        } catch (RuntimeException publishFailure) {
            redis.execute(releaseScript,
                    List.of(RedisKeys.stock(voucherId), RedisKeys.buyers(voucherId), RedisKeys.orderState(orderId)),
                    Long.toString(userId), Long.toString(orderId));
            throw publishFailure;
        }
    }

    public record AcceptedOrder(long orderId, String status) {}
}
