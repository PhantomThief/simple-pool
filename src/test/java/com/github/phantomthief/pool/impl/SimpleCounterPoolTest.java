package com.github.phantomthief.pool.impl;

import static com.google.common.util.concurrent.MoreExecutors.shutdownAndAwaitTermination;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static java.lang.System.currentTimeMillis;
import static java.lang.System.out;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.Duration;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.github.phantomthief.pool.Pool;
import com.google.common.util.concurrent.UncheckedTimeoutException;

/**
 * @author w.vela
 * Created on 09/09/2016.
 */
public class SimpleCounterPoolTest {

    private volatile boolean afterRun = false;
    private AtomicInteger executorCounter = new AtomicInteger();
    private Random random = new Random();
    private int maxConcurrencyUsingCount;

    private Set<Executor> executorSet = new CopyOnWriteArraySet<>();
    private int maxCount;

    @Test
    public void test() {
        maxCount = 30;
        maxConcurrencyUsingCount = 3;
        Duration maxIdleTime = Duration.ofSeconds(10);
        int minIdleCount = 5;
        Duration defaultWait = Duration.ofSeconds(1);

        ScheduledExecutorService scheduledExecutorService = Executors
                .newSingleThreadScheduledExecutor();
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            try {
                if (afterRun) {
                    assertTrue(executorSet.size() >= minIdleCount);
                }
                assertTrue(executorSet.size() <= maxCount);
                executorSet.forEach(executor -> {
                    assertNotNull(executor);
                    assertTrue(executor.concurrency.get() <= maxConcurrencyUsingCount);
                    if (executor.lastAccess > 0) {
                        long idle = currentTimeMillis() - executor.lastAccess;
                        boolean exceedIdle = idle < maxIdleTime.toMillis() * 2;
                        if (!exceedIdle && executorSet.size() > minIdleCount) {
                            fail("extra idle:" + idle + ", size:" + executorSet.size());
                        }
                    }
                });
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }, 50, 50, MILLISECONDS);

        Pool<Executor> pool = SimpleCounterPool.<Executor> newBuilder() //
                .setDefaultWait(defaultWait) //
                .setDestroy(Executor::close) //
                .setMaxCount(maxCount) //
                .setMaxConcurrencyUsingCount(maxConcurrencyUsingCount) //
                .setMaxIdleTime(maxIdleTime) //
                .setMinIdleCount(minIdleCount) //
                .build(Executor::new);

        ExecutorService executorService = Executors.newFixedThreadPool(60);
        for (int i = 0; i < 1000; i++) {
            int j = i;
            executorService.execute(() -> runWithTest(defaultWait, pool, j));
        }
        shutdownAndAwaitTermination(executorService, 1, DAYS);
        System.out.println("after 1 round.");
        sleepUninterruptibly(30, SECONDS);
        System.out.println("executor:" + executorSet.size());
        assertTrue(executorSet.size() == minIdleCount);

        executorService = Executors.newFixedThreadPool(200);
        for (int i = 0; i < 1000; i++) {
            int j = i;
            executorService.execute(() -> runWithTest(defaultWait, pool, j));
        }
        System.out.println("start closing...");
        pool.close();
        System.out.println("after closed...");
        assertTrue(executorSet.size() == 0);
        System.out.println("after 2 round.");
    }

    private void runWithTest(Duration defaultWait, Pool<Executor> pool, int j) {
        long s = currentTimeMillis();
        try {
            pool.supply(e -> e.convert(j));
        } catch (UncheckedTimeoutException e) {
            int size = executorSet.size();
            if (size < maxCount) {
                fail("have more max to create:" + size);
            }
            long wait = currentTimeMillis() - s;
            if (j % 100 == 0) {
                System.out.println("wait:" + wait + ", current:" + executorSet.size());
            }
            assertTrue(wait >= defaultWait.toMillis());
        }
    }

    private class Executor implements AutoCloseable {

        private final int count;
        private volatile boolean closed;
        private AtomicInteger concurrency = new AtomicInteger();
        private volatile long lastAccess;

        Executor() {
            afterRun = true;
            count = executorCounter.getAndIncrement();
            executorSet.add(this);
            if (executorSet.size() > maxCount) {
                fail("out of executor.");
            }
        }

        String convert(int i) {
            if (closed) {
                fail("executor has been closed.");
            }
            lastAccess = currentTimeMillis();
            int current = concurrency.incrementAndGet();
            if (current > maxConcurrencyUsingCount) {
                fail("invalid concurrency found," + current);
            }
            try {
                sleepUninterruptibly(random.nextInt(5 * 1000), MILLISECONDS);
                if (i % 100 == 0) {
                    out.println("executor:" + count + ", " + i + ", concurrency:" + current
                            + ", total obj:" + executorSet.size());
                }
                return i + "";
            } finally {
                concurrency.decrementAndGet();
            }
        }

        @Override
        public void close() {
            closed = true;
            assertTrue(executorSet.remove(this));
            out.println("closing:" + count + ", after closing:" + executorSet.size());
        }
    }
}