package dev.sandytang.flashdeal.service;

import dev.sandytang.flashdeal.config.RabbitConfig;
import dev.sandytang.flashdeal.domain.OrderMessage;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderConsumer {
    private final OrderCreationService orderCreation;
    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> claimScript;

    public OrderConsumer(OrderCreationService orderCreation, StringRedisTemplate redis,
                         @Qualifier("claimOrderScript") DefaultRedisScript<Long> claimOrderScript) {
        this.orderCreation = orderCreation; this.redis = redis; this.claimScript = claimOrderScript;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void consume(OrderMessage event) {
        String stateKey = RedisKeys.orderState(event.orderId());
        Long claimed = redis.execute(claimScript, List.of(stateKey));
        // 0: the publisher's confirm timed out and it already returned this reservation's stock
        if (claimed != null && claimed == 0) return;
        orderCreation.create(event);
        redis.opsForValue().set(stateKey, "CREATED");
    }
}
