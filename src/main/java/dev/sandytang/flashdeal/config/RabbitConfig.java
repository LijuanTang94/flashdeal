package dev.sandytang.flashdeal.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    public static final String EXCHANGE = "flashdeal.orders";
    public static final String QUEUE = "flashdeal.orders.create";
    public static final String ROUTING_KEY = "order.create";
    public static final String DLX = "flashdeal.orders.dlx";
    public static final String DLQ = "flashdeal.orders.dead";

    @Bean
    DirectExchange orderExchange() { return ExchangeBuilder.directExchange(EXCHANGE).durable(true).build(); }

    @Bean
    DirectExchange deadLetterExchange() { return ExchangeBuilder.directExchange(DLX).durable(true).build(); }

    @Bean
    Queue orderQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DLX).deadLetterRoutingKey("order.dead").build();
    }

    @Bean
    Queue deadLetterQueue() { return QueueBuilder.durable(DLQ).build(); }

    @Bean
    Binding orderBinding(@Qualifier("orderQueue") Queue orderQueue,
                         @Qualifier("orderExchange") DirectExchange orderExchange) {
        return BindingBuilder.bind(orderQueue).to(orderExchange).with(ROUTING_KEY);
    }

    @Bean
    Binding deadLetterBinding(@Qualifier("deadLetterQueue") Queue deadLetterQueue,
                              @Qualifier("deadLetterExchange") DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with("order.dead");
    }

    @Bean
    Jackson2JsonMessageConverter messageConverter() { return new Jackson2JsonMessageConverter(); }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, Jackson2JsonMessageConverter converter,
            RabbitTemplate rabbitTemplate) {
        var factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        factory.setPrefetchCount(50);
        var recoverer = new RepublishMessageRecoverer(rabbitTemplate, DLX, "order.dead");
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxAttempts(3).backOffOptions(200, 2.0, 2000)
                .recoverer(recoverer).build());
        return factory;
    }
}
