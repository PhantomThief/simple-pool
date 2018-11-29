package com.github.phantomthief.pool.impl;

import static com.github.phantomthief.pool.KeyAffinityExecutor.allExecutorsForStats;
import static com.github.phantomthief.pool.KeyAffinityExecutor.newSerializingExecutor;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.github.phantomthief.pool.KeyAffinityExecutor;
import com.github.phantomthief.pool.KeyAffinityExecutorStats;
import com.google.common.util.concurrent.ListeningExecutorService;

/**
 * @author w.vela
 * Created on 2018-11-29.
 */
class KeyAffinityExecutorStatsTest {

    @Test
    void test() throws Exception {
        KeyAffinityExecutor<Integer> executor1 = newSerializingExecutor(10, "test");
        KeyAffinityExecutor<Integer> executor2 = newSerializingExecutor(10, "test-2");
        Collection<KeyAffinityExecutor<?>> all = allExecutorsForStats();
        assertEquals(2, all.size());
        for (KeyAffinityExecutor<?> keyAffinityExecutor : all) {
            assertFalse(keyAffinityExecutor.inited());
        }
        executor1.executeEx(1, () -> {});
        executor2.executeEx(1, () -> {});
        for (KeyAffinityExecutor<?> keyAffinityExecutor : all) {
            assertTrue(keyAffinityExecutor.inited());
            List<ListeningExecutorService> exeList = new ArrayList<>();
            for (ListeningExecutorService executorService : keyAffinityExecutor) {
                exeList.add(executorService);
            }
            assertEquals(10, exeList.size());
            KeyAffinityExecutorStats stats = keyAffinityExecutor.stats();
            assertNotNull(stats);
            assertEquals(0, stats.getActiveThreadCount());
            assertEquals(10, stats.getParallelism());
        }

        executor1.executeEx(1, () -> sleepUninterruptibly(1, SECONDS));
        executor2.executeEx(1, () -> sleepUninterruptibly(1, SECONDS));
        for (KeyAffinityExecutor<?> keyAffinityExecutor : all) {
            KeyAffinityExecutorStats stats = keyAffinityExecutor.stats();
            assertNotNull(stats);
            assertEquals(1, stats.getActiveThreadCount());
            assertEquals(10, stats.getParallelism());
        }

        executor1.close();
        executor2.close();
        assertTrue(allExecutorsForStats().isEmpty());
    }
}
