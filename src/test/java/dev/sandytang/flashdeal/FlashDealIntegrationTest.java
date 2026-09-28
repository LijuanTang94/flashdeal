package dev.sandytang.flashdeal;

import dev.sandytang.flashdeal.persistence.VoucherOrderRepository;
import dev.sandytang.flashdeal.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.*;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
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
    @Autowired @Qualifier("releaseVoucherScript") DefaultRedisScript<Long> releaseScript;

    static final String STOCK = "flashdeal:voucher:1:stock";
    static final String BUYERS = "flashdeal:voucher:1:buyers";

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

    @Test
    void brokerNackReleasesTheReservationSoTheUserCanRetry() throws Exception {
        long userId = 90003L;
        String stockBefore = redis.opsForValue().get(STOCK);

        rejectAllPublishes(true);
        try {
            String token = (String) tokens.issue(userId, 1).get("token");
            assertThatThrownBy(() -> seckill.reserve(userId, 1, token))
                    .hasMessageContaining("not confirmed")
                    .rootCause().hasMessageContaining("nack");
        } finally {
            rejectAllPublishes(false);
        }

        assertThat(redis.opsForValue().get(STOCK)).isEqualTo(stockBefore);
        assertThat(redis.opsForSet().isMember(BUYERS, Long.toString(userId))).isFalse();
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(3))
                .until(() -> !orders.existsByVoucherIdAndUserId(1, userId));

        String retry = (String) tokens.issue(userId, 1).get("token");
        var accepted = seckill.reserve(userId, 1, retry);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(orders.findById(accepted.orderId())).isPresent());
    }

    @Test
    void lateCompensationCannotUndoACreatedOrder() {
        long userId = 90004L;
        String token = (String) tokens.issue(userId, 1).get("token");
        var accepted = seckill.reserve(userId, 1, token);
        String stateKey = "flashdeal:order:" + accepted.orderId() + ":state";
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(redis.opsForValue().get(stateKey)).isEqualTo("CREATED"));
        String stockAfterOrder = redis.opsForValue().get(STOCK);

        Long released = redis.execute(releaseScript, List.of(STOCK, BUYERS, stateKey),
                Long.toString(userId), Long.toString(accepted.orderId()));

        assertThat(released).isZero();
        assertThat(redis.opsForValue().get(STOCK)).isEqualTo(stockAfterOrder);
        assertThat(redis.opsForSet().isMember(BUYERS, Long.toString(userId))).isTrue();
    }

    // max-length 0 with reject-publish makes the broker nack every publish to the order queue
    private static void rejectAllPublishes(boolean on) throws Exception {
        String[] cmd = on
                ? new String[]{"rabbitmqctl", "set_policy", "--apply-to", "queues", "reject-all",
                        "^flashdeal\\.orders\\.create$", "{\"max-length\":0,\"overflow\":\"reject-publish\"}"}
                : new String[]{"rabbitmqctl", "clear_policy", "reject-all"};
        var result = RABBIT.execInContainer(cmd);
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
    }
}
