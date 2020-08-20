package com.github.phantomthief.pool.impl;

import static com.github.phantomthief.pool.KeyAffinityExecutor.newKeyAffinityExecutor;
import static com.github.phantomthief.pool.KeyAffinityExecutor.newSerializingExecutor;
import static com.google.common.util.concurrent.Futures.allAsList;
import static com.google.common.util.concurrent.SimpleTimeLimiter.create;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static java.util.concurrent.Executors.newFixedThreadPool;
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
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.phantomthief.pool.KeyAffinityExecutor;
import com.google.common.util.concurrent.ForwardingExecutorService;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.common.util.concurrent.TimeLimiter;

/**
 * @author w.vela
 * Created on 2020-08-14.
 */
class KeyAffinityExecutorDynamicTest {

    private static final Logger logger = LoggerFactory.getLogger(KeyAffinityExecutorDynamicTest.class);
    private static final int LOOP = 500;

    @Test
    void testDynamic() throws Throwable {
        KeyAffinityImpl.setSleepBeforeClose(0);
        int[] count = {5};
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
                .parallelism(() -> count[0])
                .counterChecker(() -> true)
                .executor(() -> new ExecutorWithStats(newSingleThreadExecutor(new ThreadFactoryBuilder().build())))
                .build();
        assertEquals(0, create[0]);
        testKeyAffinity(count[0], keyExecutor);
        assertEquals(5, create[0]);
        count[0] = 3;
        testKeyAffinity(count[0], keyExecutor);
        sleepUninterruptibly(1, SECONDS);
        assertEquals(2, shutdown[0]);
        count[0] = 7;
        testKeyAffinity(count[0], keyExecutor);
        assertEquals(9, create[0]);
        keyExecutor.close();
        assertEquals(9, shutdown[0]);
    }

    @Test
    void testFailFastCheck() {
        assertThrows(IllegalStateException.class, () ->
                newKeyAffinityExecutor()
                        .shutdownExecutorAfterClose(false)
                        .parallelism(() -> 1)
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
                    sleepUninterruptibly(100, MILLISECONDS);
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

    @Test
    void testDynamicQueue() throws TimeoutException, InterruptedException {
        TimeLimiter timeLimiter = create(newFixedThreadPool(10));
        int[] count = {10};
        KeyAffinityExecutor<Integer> executor = newSerializingExecutor(() -> 10, () -> count[0], "test");
        for (int i = 0; i < 11; i++) {
            int j = i;
            executor.executeEx(1, () -> {
                sleepUninterruptibly(1, SECONDS);
            });
        }
        assertThrows(TimeoutException.class, () ->
                timeLimiter.runWithTimeout(() -> {
                    executor.executeEx(1, () -> {
                        sleepUninterruptibly(1, SECONDS);
                    });
                }, 100, MILLISECONDS));

        count[0] = 20;
        sleepUninterruptibly(1, SECONDS);

        timeLimiter.runWithTimeout(() -> {
            executor.executeEx(1, () -> {
                sleepUninterruptibly(1, SECONDS);
            });
        }, 100, MILLISECONDS);

        count[0] = 5;
        sleepUninterruptibly(1, SECONDS);

        assertThrows(TimeoutException.class, () ->
                timeLimiter.runWithTimeout(() -> {
                    executor.executeEx(1, () -> {
                        sleepUninterruptibly(1, SECONDS);
                    });
                }, 100, MILLISECONDS));
    }
}
