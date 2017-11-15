package com.github.phantomthief.pool;

import static com.github.phantomthief.pool.impl.ConcurrencyAwarePool.CURRENT_CONCURRENCY;
import static com.github.phantomthief.pool.impl.ConcurrencyAwarePool.CURRENT_COUNT;
import static java.lang.Integer.valueOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.github.phantomthief.pool.impl.ConcurrencyAwarePool;

/**
 * @author w.vela
 * Created on 2017-11-15.
 */
class TestStats {

    @Test
    void testStats() {
        Pool<Object> pool = ConcurrencyAwarePool.builder() //
                .simpleThresholdStrategy(1, 0.7) //
                .build(String::new);
        assertNull(pool.getStats(CURRENT_COUNT));
        pool.run(System.out::println);
        assertEquals(valueOf(1), pool.getStats(CURRENT_COUNT));
        assertEquals(valueOf(0), pool.getStats(CURRENT_CONCURRENCY));
        pool.run(it -> assertEquals(valueOf(1), pool.getStats(CURRENT_CONCURRENCY)));
    }
}
