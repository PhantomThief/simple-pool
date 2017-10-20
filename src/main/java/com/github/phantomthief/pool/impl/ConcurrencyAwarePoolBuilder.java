package com.github.phantomthief.pool.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.time.Duration.ofSeconds;

import java.time.Duration;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.phantomthief.pool.Pool;
import com.github.phantomthief.util.ThrowableConsumer;
import com.github.phantomthief.util.ThrowableSupplier;

/**
 * @author w.vela
 * Created on 2017-10-18.
 */
public class ConcurrencyAwarePoolBuilder<T> {

    private static final Logger logger = LoggerFactory.getLogger(ConcurrencyAwarePoolBuilder.class);

    private static final Duration DEFAULT_EVALUATE_PERIOD = ofSeconds(1);
    private static final int DEFAULT_MIN_IDLE = 1;
    private static final int DEFAULT_MAX_SIZE = Integer.MAX_VALUE;

    ThrowableSupplier<T, Throwable> factory;
    ThrowableConsumer<T, Throwable> destroy;
    int minIdle = DEFAULT_MIN_IDLE;
    int maxSize = DEFAULT_MAX_SIZE;
    ConcurrencyAdjustStrategy<T> strategy;

    private ConcurrencyAwarePoolBuilder() {
    }

    @CheckReturnValue
    public static <T> ConcurrencyAwarePoolBuilder<T> builder() {
        return new ConcurrencyAwarePoolBuilder<>();
    }

    @CheckReturnValue
    public ConcurrencyAwarePoolBuilder<T> destroy(@Nonnull ThrowableConsumer<T, Throwable> value) {
        this.destroy = checkNotNull(value);
        return this;
    }

    @CheckReturnValue
    public ConcurrencyAwarePoolBuilder<T> minIdle(@Nonnegative int value) {
        checkArgument(value > 0);
        this.minIdle = value;
        return this;
    }

    @CheckReturnValue
    public ConcurrencyAwarePoolBuilder<T> maxSize(@Nonnegative int value) {
        checkArgument(value > 0);
        this.maxSize = value;
        return this;
    }

    @CheckReturnValue
    public ConcurrencyAwarePoolBuilder<T> strategy(@Nonnull ConcurrencyAdjustStrategy<T> strategy) {
        this.strategy = checkNotNull(strategy);
        return this;
    }

    /**
     * @param extendThreshold if average concurrency reach this threshold, the pool would extend.
     * @param shrinkThreshold if average concurrency below extendThreshold*shrinkThreshold, the pool would shrink.
     */
    @CheckReturnValue
    public ConcurrencyAwarePoolBuilder<T> simpleThresholdStrategy(@Nonnegative int extendThreshold,
            @Nonnegative double shrinkThreshold, @Nonnull Duration evaluatePeriod) {
        return strategy(new SimpleConcurrencyAdjustStrategy<>(evaluatePeriod, extendThreshold,
                shrinkThreshold));
    }

    /**
     * @param extendThreshold if average concurrency reach this threshold, the pool would extend.
     * @param shrinkThreshold if average concurrency below extendThreshold*shrinkThreshold, the pool would shrink.
     */
    @CheckReturnValue
    public ConcurrencyAwarePoolBuilder<T> simpleThresholdStrategy(@Nonnegative int extendThreshold,
            @Nonnegative double shrinkThreshold) {
        return simpleThresholdStrategy(extendThreshold, shrinkThreshold, DEFAULT_EVALUATE_PERIOD);
    }

    public Pool<T> build(@Nonnull ThrowableSupplier<T, Throwable> value) {
        this.factory = checkNotNull(value);
        ensure();
        return new LazyPool<>(() -> new ConcurrencyAwarePool<>(this));
    }

    private void ensure() {
        if (maxSize < minIdle) {
            throw new IllegalArgumentException(
                    "maxSize[" + maxSize + "] must be larger than minIdle[" + minIdle + "].");
        }
        if (strategy == null) {
            logger.warn("no strategy found. pool would run as static mode.");
        }
    }
}
