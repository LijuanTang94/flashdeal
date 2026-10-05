package dev.sandytang.flashdeal.service;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Leases a Snowflake worker id in Redis so no two live replicas share one.
 *  Costs one SET at startup and one renewal every 20 s; nextId() never touches Redis. */
@Component
public class WorkerIdLease implements DisposableBean {
    static final Duration TTL = Duration.ofSeconds(60);
    static final Duration RENEW_EVERY = Duration.ofSeconds(20);
    private static final int WORKER_IDS = 1024;

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> renewScript;
    private final DefaultRedisScript<Long> releaseScript;
    private final String owner = UUID.randomUUID().toString();
    private final long workerId;
    private final ScheduledExecutorService renewer;
    private volatile boolean held = true;

    @Autowired
    public WorkerIdLease(StringRedisTemplate redis,
            @Qualifier("renewWorkerIdScript") DefaultRedisScript<Long> renewScript,
            @Qualifier("releaseWorkerIdScript") DefaultRedisScript<Long> releaseScript,
            @Value("${flashdeal.worker-id:-1}") long configuredWorkerId) {
        this(redis, renewScript, releaseScript, configuredWorkerId, hostname());
    }

    WorkerIdLease(StringRedisTemplate redis, DefaultRedisScript<Long> renewScript,
            DefaultRedisScript<Long> releaseScript, long configuredWorkerId, String hostname) {
        if (configuredWorkerId >= WORKER_IDS) throw new IllegalArgumentException("worker-id must be 0..1023");
        this.redis = redis; this.renewScript = renewScript; this.releaseScript = releaseScript;
        this.workerId = configuredWorkerId >= 0 ? claimExactly(configuredWorkerId)
                : claimFrom((hostname.hashCode() & 0x7fffffff) % WORKER_IDS);
        this.renewer = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name("worker-id-lease").daemon().factory());
        long period = RENEW_EVERY.toMillis();
        renewer.scheduleAtFixedRate(this::renew, period, period, TimeUnit.MILLISECONDS);
    }

    public long workerId() { return workerId; }

    /** False once another instance has taken this id over; the generator must stop minting. */
    public boolean held() { return held; }

    void renew() {
        try {
            Long ok = redis.execute(renewScript, List.of(RedisKeys.workerId(workerId)),
                    owner, Long.toString(TTL.toMillis()));
            if (ok != null && ok == 0) held = false;
        } catch (RuntimeException e) {
            // Redis unreachable: the lease stays valid until its TTL runs out, so retry on the next tick.
        }
    }

    @Override
    public void destroy() {
        renewer.shutdownNow();
        try {
            redis.execute(releaseScript, List.of(RedisKeys.workerId(workerId)), owner);
        } catch (RuntimeException e) {
            // The TTL frees the id anyway.
        }
    }

    private long claimFrom(long start) {
        // Hostname hashes can collide, so probe forward to the next free id.
        for (int i = 0; i < WORKER_IDS; i++) {
            long id = (start + i) % WORKER_IDS;
            if (tryClaim(id)) return id;
        }
        throw new IllegalStateException("all " + WORKER_IDS + " worker ids are leased");
    }

    private long claimExactly(long id) {
        if (!tryClaim(id)) throw new IllegalStateException("worker-id " + id + " is already leased by another instance");
        return id;
    }

    private boolean tryClaim(long id) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(RedisKeys.workerId(id), owner, TTL));
    }

    private static String hostname() {
        try { return java.net.InetAddress.getLocalHost().getHostName(); }
        catch (Exception e) { return Long.toString(ProcessHandle.current().pid()); }
    }
}
