package com.github.phantomthief.pool.impl;

import static com.github.phantomthief.pool.impl.DynamicCapacityLinkedBlockingQueue.lazyDynamicCapacityLinkedBlockingQueue;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.BlockingQueue;

import org.junit.jupiter.api.Test;

/**
 * @author w.vela
 * Created on 2020-08-20.
 */
class DynamicCapacityLinkedBlockingQueueTest {

    @Test
    void test() throws InterruptedException {
        int[] count = {0};
        boolean[] access = {false};
        BlockingQueue<Object> queue = lazyDynamicCapacityLinkedBlockingQueue(() -> {
            access[0] = true;
            return count[0];
        });
        assertFalse(access[0]);
        queue.put("test");
        assertTrue(access[0]);
        assertEquals("test", queue.poll());
        assertTrue(queue.isEmpty());

        count[0] = 10;
        sleepUninterruptibly(1, SECONDS);

        for (int i = 0; i < 10; i++) {
            assertTrue(queue.offer("test"));
        }
        assertFalse(queue.offer("test"));

        count[0] = 20;
        sleepUninterruptibly(1, SECONDS);

        for (int i = 0; i < 10; i++) {
            assertTrue(queue.offer("test"));
        }
        assertFalse(queue.offer("test"));

        count[0] = 5;
        sleepUninterruptibly(1, SECONDS);

        assertFalse(queue.offer("test"));

        for (int i = 0; i < 15; i++) {
            assertEquals("test", queue.poll());
        }

        assertFalse(queue.offer("test"));
        assertEquals("test", queue.poll());
        assertTrue(queue.offer("test"));
    }
}