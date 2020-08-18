package com.github.phantomthief.pool.impl;

import static com.github.phantomthief.pool.KeyAffinityExecutor.newKeyAffinityExecutor;
import static com.google.common.util.concurrent.Futures.allAsList;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import org.junit.jupiter.api.Test;

import com.github.phantomthief.pool.KeyAffinityExecutor;
import com.google.common.util.concurrent.ForwardingExecutorService;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

/**
 * @author w.vela
 * Created on 2020-08-14.
 */
class KeyAffinityExecutorDynamicTest {

    private static final int LOOP = 1000;

    @Test
    void testDynamic() throws Throwable {
        int[] count = {10};
        int[] create = {0};
        int[] shutdown = {0};
        class ExecutorWithStats extends ForwardingExecutorService {

            private final ExecutorService executor;

            ExecutorWithStats(ExecutorService executor) {
                create[0]++;
                this.executor = executor;
            }

            @Override
            protected ExecutorService delegate() {
                return executor;
            }

            @Override
            public void shutdown() {
                shutdown[0]++;
                super.shutdown();
            }
        }
        KeyAffinityExecutor<Integer> keyExecutor = newKeyAffinityExecutor()
                .count(() -> count[0])
                .executor(() -> new ExecutorWithStats(newSingleThreadExecutor(new ThreadFactoryBuilder().build())))
                .build();
        assertEquals(0, create[0]);
        testKeyAffinity(count[0], keyExecutor);
        assertEquals(10, create[0]);
        count[0] = 5;
        testKeyAffinity(count[0], keyExecutor);
        sleepUninterruptibly(5, SECONDS);
        assertEquals(5, shutdown[0]);
        count[0] = 15;
        testKeyAffinity(count[0], keyExecutor);
        assertEquals(20, create[0]);
        keyExecutor.close();
        assertEquals(20, shutdown[0]);
    }

    @Test
    void testFailFastCheck() {
        assertThrows(IllegalStateException.class, () ->
                newKeyAffinityExecutor()
                        .shutdownExecutorAfterClose(false)
                        .count(() -> 1)
                        .build());
    }

    private void testKeyAffinity(int expected, KeyAffinityExecutor<Integer> keyExecutor) throws Throwable {
        Set<String> threads = ConcurrentHashMap.newKeySet();
        Set<Integer> duplicateKey = ConcurrentHashMap.newKeySet();
        List<ListenableFuture<?>> futures = new ArrayList<>();
        Throwable[] assertionError = {null};
        for (int i = 0; i < LOOP; i++) {
            int key = i % 50;
            ListenableFuture<String> submit = keyExecutor.submit(key, () -> {
                try {
                    assertFalse(duplicateKey.contains(key));
                    boolean add = duplicateKey.add(key);
                    assertTrue(add);
                    String e = currentThreadIdentity();
                    threads.add(e);
                    sleepUninterruptibly(500, MILLISECONDS);
                    return e;
                } catch (Throwable e) {
                    assertionError[0] = e;
                    throw e;
                } finally {
                    duplicateKey.remove(key);
                }
            });
            futures.add(submit);
        }
        allAsList(futures.toArray(new ListenableFuture[0])).get();
        assertEquals(expected, threads.size());
        if (assertionError[0] != null) {
            throw assertionError[0];
        }
    }

    private String currentThreadIdentity() {
        Thread thread = Thread.currentThread();
        return thread.toString() + "/" + thread.hashCode();
    }
}
