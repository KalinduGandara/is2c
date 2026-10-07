package com.is2c.hrm.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.is2c.hrm.platform.db.DatabasePlatform;
import com.is2c.hrm.support.IntegrationTest;

@IntegrationTest
class DatabasePlatformTest {

    @Autowired
    DatabasePlatform platform;

    @Test
    @DisplayName("NFR-005 postgres platform is the active vendor")
    void vendor() {
        assertThat(platform.vendor()).isEqualTo("postgresql");
    }

    @Test
    @DisplayName("FR-014 sequence values are unique and gapless under concurrency")
    void concurrentSequence() throws Exception {
        UUID company = UUID.randomUUID();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Long>> calls = IntStream.range(0, 50)
                    .<Callable<Long>>mapToObj(i -> () -> platform.nextSequenceValue(company, "EMP"))
                    .toList();
            List<Long> values = pool.invokeAll(calls).stream().map(DatabasePlatformTest::get).toList();
            assertThat(values).doesNotHaveDuplicates().hasSize(50);
            assertThat(values).allMatch(v -> v >= 1 && v <= 50);
        } finally {
            pool.shutdown();
        }
        assertThat(platform.nextSequenceValue(company, "OTHER")).isEqualTo(1);
    }

    private static Long get(Future<Long> f) {
        try {
            return f.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
