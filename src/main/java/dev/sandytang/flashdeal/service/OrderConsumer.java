package dev.sandytang.flashdeal.service;

import dev.sandytang.flashdeal.config.RabbitConfig;
import dev.sandytang.flashdeal.domain.OrderMessage;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderConsumer {
    private final OrderCreationService orderCreation;
    private final StringRedisTemplate redis;

    public OrderConsumer(OrderCreationService orderCreation, StringRedisTemplate redis) {
        this.orderCreation = orderCreation; this.redis = redis;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void consume(OrderMessage event) {
        orderCreation.create(event);
        redis.opsForValue().set(RedisKeys.orderState(event.orderId()), "CREATED");
    }
}
