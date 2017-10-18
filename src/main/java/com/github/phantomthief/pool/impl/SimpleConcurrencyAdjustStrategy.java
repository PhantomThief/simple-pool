package com.github.phantomthief.pool.impl;

import static com.github.phantomthief.pool.impl.ConcurrencyAdjustStrategy.noChange;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.math.RoundingMode.CEILING;
import static java.util.Collections.singleton;

import java.time.Duration;
import java.util.Set;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.math.IntMath;

/**
 * extend or shrink only one item on each cycle.
 *
 * @author w.vela
 * Created on 2017-10-18.
 */
class SimpleConcurrencyAdjustStrategy<T> implements ConcurrencyAdjustStrategy<T> {

    private final Duration period;
    private final int extendThreshold;
    private final double shrinkThreshold;

    SimpleConcurrencyAdjustStrategy(@Nonnull Duration period, @Nonnegative int extendThreshold,
            @Nonnegative double shrinkThreshold) {
        checkArgument(extendThreshold > 0);
        checkArgument(shrinkThreshold > 0 && shrinkThreshold < 1);
        this.period = checkNotNull(period);
        this.extendThreshold = extendThreshold;
        this.shrinkThreshold = shrinkThreshold;
    }

    @Nonnull
    @Override
    public Duration evaluatePeriod() {
        return period;
    }

    @Nullable
    @Override
    public AdjustResult<T> adjust(@Nonnull Set<CurrentObject<T>> current) {
        int itemSize = current.size();
        CurrentObject<T> minCurrentObject = null;
        int sumConcurrency = 0;
        for (CurrentObject<T> object : current) {
            sumConcurrency += object.getCurrentConcurrency();
            if (minCurrentObject == null) {
                minCurrentObject = object;
            } else if (minCurrentObject.getCurrentConcurrency() > object.getCurrentConcurrency()) {
                minCurrentObject = object;
            }
        }
        int currentAvgConcurrency = IntMath.divide(sumConcurrency, itemSize, CEILING);
        if (currentAvgConcurrency > extendThreshold) {
            return new AdjustResult<>(null, 1);
        }
        if (shrinkThreshold * extendThreshold * itemSize < sumConcurrency) {
            return noChange();
        }
        if (itemSize <= 1) {
            return noChange();
        }
        int afterReduceAvgConcurrency = IntMath.divide(sumConcurrency, itemSize - 1, CEILING);
        if (extendThreshold > afterReduceAvgConcurrency) {
            return new AdjustResult<>(singleton(minCurrentObject), 0);
        } else {
            return noChange();
        }
    }
}
