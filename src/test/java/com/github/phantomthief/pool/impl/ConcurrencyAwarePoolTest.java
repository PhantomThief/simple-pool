package com.github.phantomthief.pool.impl;

import static com.github.phantomthief.pool.impl.ConcurrencyAwarePool.CURRENT_COUNT;
import static com.google.common.util.concurrent.MoreExecutors.shutdownAndAwaitTermination;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static java.time.Duration.ofSeconds;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.phantomthief.pool.Pool;
import com.google.common.util.concurrent.UncheckedTimeoutException;

/**
 * @author w.vela
 * Created on 09/09/2016.
 */
class ConcurrencyAwarePoolTest {

    private static final Logger logger = LoggerFactory.getLogger(ConcurrencyAwarePoolTest.class);
    private volatile boolean afterRun = false;
    private AtomicInteger executorCounter = new AtomicInteger();

    private Set<Executor> executorSet = new CopyOnWriteArraySet<>();
    private int maxCount;
    private volatile boolean closing;

    @Test
    void test() {
        maxCount = 20;
        int extendThreshold = 10;
        int minIdleCount = 2;
        double shrinkThreshold = 0.5D;

        newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            if (closing) {
                return;
            }
            try {
                if (afterRun) {
                    assertTrue(executorSet.size() >= minIdleCount);
                }
                assertTrue(executorSet.size() <= maxCount);
                executorSet.forEach(Assertions::assertNotNull);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }, 50, 50, MILLISECONDS);

        Pool<Executor> pool = ConcurrencyAwarePool.<Executor> builder()
                .destroy(Executor::close)
                .maxSize(maxCount)
                .minIdle(minIdleCount)
                .evaluatePeriod(ofSeconds(1))
                .simpleThresholdStrategy(extendThreshold, shrinkThreshold)
                .build(Executor::new);
        logger.info("after create pool.");
        pool.run(o -> {});
        afterRun = true;

        ExecutorService executorService = newFixedThreadPool(60);
        for (int i = 0; i < 500; i++) {
            int j = i;
            executorService.execute(() -> runWithTest(pool, j));
        }
        logger.info("current count:{}", pool.getStats(CURRENT_COUNT));
        logger.info("waiting closing...");
        shutdownAndAwaitTermination(executorService, 1, DAYS);
        logger.info("after 1 round.");
        sleepUninterruptibly(15, SECONDS);
        logger.info("executor:{}", executorSet.size());
        assertTrue(executorSet.size() == minIdleCount);

        executorService = newFixedThreadPool(300);
        for (int i = 0; i < 3000; i++) {
            int j = i;
            executorService.execute(() -> runWithTest(pool, j));
        }
        shutdownAndAwaitTermination(executorService, 1, DAYS);

        executorService = newFixedThreadPool(30);
        for (int i = 0; i < 1000; i++) {
            int j = i;
            executorService.execute(() -> runWithTest(pool, j));
        }
        shutdownAndAwaitTermination(executorService, 1, DAYS);

        logger.info("start closing...");
        closing = true;
        pool.close();
        logger.info("after closed...");
        assertTrue(executorSet.size() == 0);
        logger.info("after 2 round.");
    }

    @Test
    void testIllegal() {
        Pool<String> pool = ConcurrencyAwarePool.<String> builder()
                .build(() -> "test");
        pool.run(s -> logger.info("{}", s));
        pool.close();
        assertThrows(IllegalStateException.class, pool::borrow);
        assertThrows(IllegalArgumentException.class, () -> ConcurrencyAwarePool.<String> builder()
                .minIdle(10).maxSize(5).build(() -> "test"));
    }

    private void runWithTest(Pool<Executor> pool, int j) {
        try {
            pool.supply(e -> e.convert(j));
        } catch (UncheckedTimeoutException e) {
            int size = executorSet.size();
            if (size < maxCount) {
                fail("have more max to create:" + size);
            }
        }
    }

    private class Executor implements AutoCloseable {

        private final int count;
        private volatile boolean closed;
        private AtomicInteger concurrency = new AtomicInteger();

        Executor() {
            count = executorCounter.getAndIncrement();
            executorSet.add(this);
            if (executorSet.size() > maxCount) {
                fail("out of executor.");
            }
            logger.info("create obj, after count:{}", executorSet.size());
        }

        String convert(int i) {
            if (closed) {
                fail("executor has been closed.");
            }
            int current = concurrency.incrementAndGet();
            try {
                sleepUninterruptibly(ThreadLocalRandom.current().nextInt(5 * 1000), MILLISECONDS);
                if (i % 100 == 0) {
                    logger.info("executor:{}, {}, concurrency:{}, total obj:{}", count, i, current,
                            executorSet.size());
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
            logger.info("closing:{}, after closing:{}", count, executorSet.size());
        }
    }
}