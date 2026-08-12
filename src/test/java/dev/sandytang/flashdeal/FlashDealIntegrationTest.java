package dev.sandytang.flashdeal;

import dev.sandytang.flashdeal.persistence.VoucherOrderRepository;
import dev.sandytang.flashdeal.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.*;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest
class FlashDealIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("flashdeal").withUsername("flashdeal").withPassword("flashdeal");
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379);
    @Container static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.0-management-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
        registry.add("flashdeal.token-ttl", () -> "PT30S");
    }

    @Autowired SeckillTokenService tokens;
    @Autowired SeckillService seckill;
    @Autowired VoucherOrderRepository orders;
    @Autowired StringRedisTemplate redis;

    @Test
    void acceptsThenPersistsAnOrderExactlyOnce() {
        long userId = 90001L;
        String token = (String) tokens.issue(userId, 1).get("token");
        var accepted = seckill.reserve(userId, 1, token);

        assertThat(accepted.status()).isEqualTo("QUEUED");
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(orders.findById(accepted.orderId())).isPresent());
        assertThat(orders.existsByVoucherIdAndUserId(1, userId)).isTrue();
        assertThat(redis.opsForValue().get("flashdeal:order:" + accepted.orderId() + ":state"))
                .isEqualTo("CREATED");
    }

    @Test
    void rejectsASecondOrderForTheSameVoucher() {
        long userId = 90002L;
        String first = (String) tokens.issue(userId, 1).get("token");
        seckill.reserve(userId, 1, first);
        String second = (String) tokens.issue(userId, 1).get("token");

        assertThatThrownBy(() -> seckill.reserve(userId, 1, second))
                .isInstanceOfSatisfying(dev.sandytang.flashdeal.domain.SeckillRejectedException.class,
                        ex -> assertThat(ex.code()).isEqualTo("DUPLICATE_ORDER"));
    }
}
