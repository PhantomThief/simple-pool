package com.github.phantomthief.pool.impl;

import static com.google.common.collect.ImmutableSet.of;
import static java.lang.System.currentTimeMillis;
import static java.time.Duration.ofSeconds;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.github.phantomthief.pool.impl.ConcurrencyAdjustStrategy.AdjustResult;
import com.github.phantomthief.pool.impl.ConcurrencyAdjustStrategy.CurrentObject;

/**
 * @author w.vela
 * Created on 2017-10-18.
 */
public class SimpleConcurrencyAdjustStrategyTest {

    @Test
    public void testAdjust() {
        SimpleConcurrencyAdjustStrategy<String> strategy = new SimpleConcurrencyAdjustStrategy<>(
                ofSeconds(1), 10, 0.5);
        // heavy to expend
        AdjustResult<String> adjust = strategy
                .adjust(of(new CurrentObject<>("test1", 20, currentTimeMillis())));
        assertNotNull(adjust);
        assertTrue(adjust.getCreate() == 1);

        // idle to shrink
        CurrentObject<String> toEvict = new CurrentObject<>("test2", 4, currentTimeMillis());
        adjust = strategy.adjust(of( //
                new CurrentObject<>("test1", 5, currentTimeMillis()), //
                toEvict));
        assertNotNull(adjust);
        assertTrue(adjust.getCreate() == 0);
        assertTrue(toEvict == adjust.getEvict().iterator().next());

        strategy = new SimpleConcurrencyAdjustStrategy<>(ofSeconds(1), 10, 0.9);
        adjust = strategy.adjust(of( //
                new CurrentObject<>("test1", 9, currentTimeMillis()), //
                new CurrentObject<>("test2", 9, currentTimeMillis()), //
                new CurrentObject<>("test3", 8, currentTimeMillis()) //
        ));
        assertNull(adjust);

        toEvict = new CurrentObject<>("test3", 1, currentTimeMillis());
        adjust = strategy.adjust(of( //
                new CurrentObject<>("test1", 9, currentTimeMillis()), //
                new CurrentObject<>("test2", 8, currentTimeMillis()), //
                toEvict //
        ));
        assertNotNull(adjust);
        assertTrue(adjust.getCreate() == 0);
        assertTrue(toEvict == adjust.getEvict().iterator().next());

        adjust = strategy.adjust(of(new CurrentObject<>("test1", 1, currentTimeMillis())));
        assertNull(adjust);

        adjust = strategy.adjust(of( //
                new CurrentObject<>("test1", 9, currentTimeMillis()),
                new CurrentObject<>("test2", 10, currentTimeMillis())));
        assertNull(adjust);
    }
}