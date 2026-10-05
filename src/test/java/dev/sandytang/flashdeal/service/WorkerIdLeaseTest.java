package dev.sandytang.flashdeal.service;

import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class WorkerIdLeaseTest {
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379);

    static LettuceConnectionFactory factory;
    static StringRedisTemplate redis;
    final List<WorkerIdLease> leases = new ArrayList<>();

    @BeforeAll
    static void connect() {
        factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        factory.afterPropertiesSet();
        factory.start();
        redis = new StringRedisTemplate(factory);
    }

    @AfterAll
    static void disconnect() { factory.destroy(); }

    @AfterEach
    void releaseAll() {
        leases.forEach(WorkerIdLease::destroy);
        leases.clear();
        redis.execute((RedisCallback<Void>) connection -> { connection.serverCommands().flushAll(); return null; });
    }

    @Test
    void replicasWhoseHostnamesHashToTheSameIdGetDistinctIds() {
        var first = lease(-1, "app-1");
        var second = lease(-1, "app-1");

        assertThat(second.workerId()).isEqualTo((first.workerId() + 1) % 1024);
    }

    @Test
    void anExplicitWorkerIdThatIsAlreadyLeasedFailsStartup() {
        lease(7, "app-1");

        assertThatThrownBy(() -> lease(7, "app-2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already leased");
    }

    @Test
    void shutdownFreesTheIdForTheNextInstance() {
        var first = lease(7, "app-1");
        first.destroy();
        leases.remove(first);

        assertThat(lease(7, "app-2").workerId()).isEqualTo(7);
    }

    @Test
    void renewalDetectsATakeoverAndTheGeneratorStopsMinting() {
        var lease = lease(7, "app-1");
        var ids = new SnowflakeIdGenerator(lease);
        redis.opsForValue().set("flashdeal:worker:7", "another-instance");

        lease.renew();

        assertThat(lease.held()).isFalse();
        assertThatThrownBy(ids::nextId).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void renewalRetakesAnExpiredLeaseNobodyClaimed() {
        var lease = lease(7, "app-1");
        redis.delete("flashdeal:worker:7");

        lease.renew();

        assertThat(lease.held()).isTrue();
        assertThat(redis.getExpire("flashdeal:worker:7")).isPositive();
    }

    WorkerIdLease lease(long configuredWorkerId, String hostname) {
        var lease = new WorkerIdLease(redis, script("renew_worker_id.lua"), script("release_worker_id.lua"),
                configuredWorkerId, hostname);
        leases.add(lease);
        return lease;
    }

    static DefaultRedisScript<Long> script(String name) {
        var script = new DefaultRedisScript<Long>();
        script.setLocation(new ClassPathResource("scripts/" + name));
        script.setResultType(Long.class);
        return script;
    }
}
