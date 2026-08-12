package dev.sandytang.flashdeal.service;

import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

class SnowflakeIdGeneratorTest {
    @Test
    void generatesUniqueIdsAcrossThreads() throws Exception {
        var generator = new SnowflakeIdGenerator(7);
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        var pool = Executors.newFixedThreadPool(8);
        try {
            var tasks = java.util.stream.IntStream.range(0, 10_000)
                    .mapToObj(i -> (Callable<Void>) () -> { ids.add(generator.nextId()); return null; })
                    .toList();
            for (Future<Void> future : pool.invokeAll(tasks)) future.get();
        } finally {
            pool.shutdownNow();
        }
        assertThat(ids).hasSize(10_000);
    }
}
