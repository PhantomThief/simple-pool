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
        List<? extends ConcurrencyInfo> minList = current.stream() //
                .sorted(comparingInt(ConcurrencyInfo::currentConcurrency)) //
                .limit(2) //
                .collect(toList());
        ConcurrencyInfo first = minList.get(0);
        if (first.currentConcurrency() >= extendThreshold) {
            return new AdjustResult(null, 1);
        }
        if (minList.size() == 1) {
            return NO_CHANGE;
        }
        ConcurrencyInfo second = minList.get(1);
        if (second.currentConcurrency() < extendThreshold * shrinkThreshold) {
            return new AdjustResult(singleton(first), 0);
        } else {
            return NO_CHANGE;
        }
    }
}
