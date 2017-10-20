package com.github.phantomthief.pool.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static java.math.RoundingMode.CEILING;
import static java.util.Collections.singleton;

import java.util.Collection;

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
class SimpleConcurrencyAdjustStrategy implements ConcurrencyAdjustStrategy {

    private final int extendThreshold;
    private final double shrinkThreshold;

    SimpleConcurrencyAdjustStrategy(@Nonnegative int extendThreshold,
            @Nonnegative double shrinkThreshold) {
        checkArgument(extendThreshold > 0);
        checkArgument(shrinkThreshold > 0 && shrinkThreshold < 1);
        this.extendThreshold = extendThreshold;
        this.shrinkThreshold = shrinkThreshold;
    }

    @Nullable
    @Override
    public AdjustResult adjust(@Nonnull Collection<? extends ConcurrencyInfo> current) {
        int itemSize = current.size();
        ConcurrencyInfo minConcurrencyInfo = null;
        int sumConcurrency = 0;
        for (ConcurrencyInfo object : current) {
            sumConcurrency += object.currentConcurrency();
            if (minConcurrencyInfo == null) {
                minConcurrencyInfo = object;
            } else if (minConcurrencyInfo.currentConcurrency() > object.currentConcurrency()) {
                minConcurrencyInfo = object;
            }
        }
        int currentAvgConcurrency = IntMath.divide(sumConcurrency, itemSize, CEILING);
        if (currentAvgConcurrency > extendThreshold) {
            return new AdjustResult(null, 1);
        }
        if (shrinkThreshold * extendThreshold * itemSize < sumConcurrency) {
            return NO_CHANGE;
        }
        if (itemSize <= 1) {
            return NO_CHANGE;
        }
        int afterReduceAvgConcurrency = IntMath.divide(sumConcurrency, itemSize - 1, CEILING);
        if (extendThreshold > afterReduceAvgConcurrency) {
            return new AdjustResult(singleton(minConcurrencyInfo), 0);
        } else {
            return NO_CHANGE;
        }
    }
}
