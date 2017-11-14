package com.github.phantomthief.pool.impl;

import static com.google.common.collect.ImmutableSet.of;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.github.phantomthief.pool.impl.ConcurrencyAdjustStrategy.AdjustResult;

/**
 * @author w.vela
 * Created on 2017-10-18.
 */
class SimpleConcurrencyAdjustStrategyTest {

    @Test
    void testAdjust() {
        SimpleConcurrencyAdjustStrategy strategy = new SimpleConcurrencyAdjustStrategy(10, 0.5);
        // heavy to expend
        AdjustResult adjust = strategy.adjust(of(new MyConcurrencyInfo(20)));
        assertNotNull(adjust);
        assertTrue(adjust.getCreate() == 1);

        // idle to shrink
        ConcurrencyInfo toEvict = new MyConcurrencyInfo(4);
        adjust = strategy.adjust(of( //
                new MyConcurrencyInfo(5), //
                toEvict));
        assertNotNull(adjust);
        assertTrue(adjust.getCreate() == 0);
        assertNotNull(adjust.getEvict());
        assertTrue(toEvict == adjust.getEvict().iterator().next());

        strategy = new SimpleConcurrencyAdjustStrategy( 10, 0.9);
        adjust = strategy.adjust(of( //
                new MyConcurrencyInfo(9), //
                new MyConcurrencyInfo(9), //
                new MyConcurrencyInfo(8) //
        ));
        assertNull(adjust);

        toEvict = new MyConcurrencyInfo(1);
        adjust = strategy.adjust(of( //
                new MyConcurrencyInfo(9), //
                new MyConcurrencyInfo(8), //
                toEvict //
        ));
        assertNotNull(adjust);
        assertTrue(adjust.getCreate() == 0);
        assertNotNull(adjust.getEvict());
        assertTrue(toEvict == adjust.getEvict().iterator().next());

        adjust = strategy.adjust(of(new MyConcurrencyInfo(1)));
        assertNull(adjust);

        adjust = strategy.adjust(of( //
                new MyConcurrencyInfo(9), new MyConcurrencyInfo(10)));
        assertNull(adjust);
    }

    private static class MyConcurrencyInfo implements ConcurrencyInfo {

        private final int currentConcurrency;

        private MyConcurrencyInfo(int currentConcurrency) {
            this.currentConcurrency = currentConcurrency;
        }

        @Override
        public int currentConcurrency() {
            return currentConcurrency;
        }
    }
}