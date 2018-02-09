package com.github.phantomthief.pool.impl;

import static com.github.phantomthief.pool.KeyAffinityExecutor.newSerializingExecutor;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.phantomthief.pool.KeyAffinityExecutor;

/**
 * @author w.vela
 * Created on 2018-02-09.
 */
class KeyAffinityExecutorTest {

    private static final Logger logger = LoggerFactory.getLogger(KeyAffinityExecutorTest.class);
    private KeyAffinityExecutor<Integer> keyExecutor;

    @BeforeEach
    void setUp() {
        keyExecutor = newSerializingExecutor(10, "s-%d");
    }

    @Test
    void test() {
        Map<Integer, String> firstMapping = new ConcurrentHashMap<>();
        for (int i = 0; i < 20; i++) {
            int j = i;
            keyExecutor.execute(j, () -> {
                firstMapping.put(j, currentThreadIdentity());
                sleepUninterruptibly(10, SECONDS);
            });
        }
        sleepUninterruptibly(1, SECONDS);
        AtomicInteger counter = new AtomicInteger();
        int loop = 1000;
        for (int i = 0; i < loop; i++) {
            int key = ThreadLocalRandom.current().nextInt(20);
            keyExecutor.execute(key, () -> {
                String firstV = firstMapping.get(key);
                assertEquals(firstV, currentThreadIdentity());
                counter.incrementAndGet();
            });
        }
        sleepUninterruptibly(3, SECONDS);
        logger.info("gathered threads:{}", firstMapping);
        assertEquals(loop, counter.get());
    }

    private String currentThreadIdentity() {
        Thread thread = Thread.currentThread();
        return thread.toString() + "/" + thread.hashCode();
    }

    @AfterEach
    void tearDown() throws Exception {
        keyExecutor.close();
    }
}
