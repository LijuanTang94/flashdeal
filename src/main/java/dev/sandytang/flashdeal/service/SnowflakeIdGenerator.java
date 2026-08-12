package dev.sandytang.flashdeal.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class SnowflakeIdGenerator {
    private static final long EPOCH = 1735689600000L; // 2025-01-01 UTC
    private final long workerId;
    private final AtomicLong sequence = new AtomicLong();
    private volatile long lastMillis = -1;

    public SnowflakeIdGenerator(@Value("${flashdeal.worker-id:1}") long workerId) {
        if (workerId < 0 || workerId > 1023) throw new IllegalArgumentException("worker-id must be 0..1023");
        this.workerId = workerId;
    }

    public synchronized long nextId() {
        long now = Instant.now().toEpochMilli();
        if (now < lastMillis) throw new IllegalStateException("Clock moved backwards");
        if (now != lastMillis) { sequence.set(0); lastMillis = now; }
        long seq = sequence.getAndIncrement() & 0xFFF;
        if (seq == 0 && sequence.get() > 1) {
            do { now = Instant.now().toEpochMilli(); } while (now <= lastMillis);
            lastMillis = now; sequence.set(1);
        }
        return ((now - EPOCH) << 22) | (workerId << 12) | seq;
    }
}
