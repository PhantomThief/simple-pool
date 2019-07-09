package com.github.phantomthief.pool.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Collections.singleton;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toList;

import java.util.Collection;
import java.util.List;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * extend or shrink at most one item on each cycle.
 *
 * @author w.vela
 * Created on 2017-10-18.
 */
@NotThreadSafe
class SimpleConcurrencyAdjustStrategy implements ConcurrencyAdjustStrategy {

    private final int extendThreshold;
    private final double shrinkThreshold;
    private final int continuousExtendThreshold;
    private final int continuousShrinkThreshold;

    private int continuousExtendCount;
    private int continuousShrinkCount;

    SimpleConcurrencyAdjustStrategy(@Nonnegative int extendThreshold,
            @Nonnegative double shrinkThreshold, @Nonnegative int continuousExtendThreshold,
            @Nonnegative int continuousShrinkThreshold) {
        checkArgument(continuousExtendThreshold > 0);
        checkArgument(continuousShrinkThreshold > 0);
        checkArgument(extendThreshold > 0);
        checkArgument(shrinkThreshold > 0 && shrinkThreshold < 1);
        this.continuousExtendThreshold = continuousExtendThreshold;
        this.continuousShrinkThreshold = continuousShrinkThreshold;
        this.extendThreshold = extendThreshold;
        this.shrinkThreshold = shrinkThreshold;
    }

    @Nullable
    @Override
    public AdjustResult adjust(@Nonnull Collection<? extends ConcurrencyInfo> current) {
        List<? extends ConcurrencyInfo> minList = current.stream()
                .sorted(comparingInt(ConcurrencyInfo::currentConcurrency))
                .limit(2)
                .collect(toList());
        ConcurrencyInfo first = minList.get(0);
        if (first.currentConcurrency() >= extendThreshold) {
            continuousExtendCount++;
            if (continuousExtendCount == continuousExtendThreshold) {
                resetContinuousCounter();
                return new AdjustResult(null, 1);
            } else {
                continuousShrinkCount = 0;
                return NO_CHANGE;
            }
        }
        if (minList.size() == 1) {
            resetContinuousCounter();
            return NO_CHANGE;
        }
        ConcurrencyInfo second = minList.get(1);
        if (second.currentConcurrency() < extendThreshold * shrinkThreshold) {
            continuousShrinkCount++;
            if (continuousShrinkCount == continuousShrinkThreshold) {
                resetContinuousCounter();
                return new AdjustResult(singleton(first), 0);
            } else {
                continuousExtendCount = 0;
                return NO_CHANGE;
            }
        } else {
            resetContinuousCounter();
            return NO_CHANGE;
        }
    }

    private void resetContinuousCounter() {
        continuousExtendCount = 0;
        continuousShrinkCount = 0;
    }
}
