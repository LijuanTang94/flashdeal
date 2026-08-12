package dev.sandytang.flashdeal.service;

import dev.sandytang.flashdeal.config.RabbitConfig;
import dev.sandytang.flashdeal.domain.OrderMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class OrderPublisher {
    private final RabbitTemplate rabbit;
    public OrderPublisher(RabbitTemplate rabbit) { this.rabbit = rabbit; }

    public void publish(OrderMessage event) {
        var correlation = new CorrelationData(Long.toString(event.orderId()));
        rabbit.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, event, message -> {
            message.getMessageProperties().setMessageId(Long.toString(event.orderId()));
            message.getMessageProperties().setHeader("x-event-version", 1);
            message.getMessageProperties().setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
            return message;
        }, correlation);
        try {
            CorrelationData.Confirm confirm = correlation.getFuture().get(2, TimeUnit.SECONDS);
            if (!confirm.isAck()) throw new IllegalStateException("Broker nack: " + confirm.getReason());
        } catch (Exception e) {
            throw new IllegalStateException("Order event was not confirmed", e);
        }
    }
}
