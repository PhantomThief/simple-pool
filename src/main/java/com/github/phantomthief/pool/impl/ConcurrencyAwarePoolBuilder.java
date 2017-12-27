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
    private static final int DEFAULT_MAX_SIZE = 10000;
    private static final int DEFAULT_CONTINUOUS_EXTEND_THRESHOLD = 1;
    private static final int DEFAULT_CONTINUOUS_SHRINK_THRESHOLD = 1;

    ThrowableSupplier<T, Exception> factory;
    ThrowableConsumer<T, Exception> destroy;
    int minIdle = DEFAULT_MIN_IDLE;
    int maxSize = DEFAULT_MAX_SIZE;
    ConcurrencyAdjustStrategy strategy;
    Duration evaluatePeriod = DEFAULT_EVALUATE_PERIOD;

    ConcurrencyAwarePoolBuilder() {
    }

    @CheckReturnValue
    public ConcurrencyAwarePoolBuilder<T> destroy(@Nonnull ThrowableConsumer<T, Exception> value) {
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
    public ConcurrencyAwarePoolBuilder<T> strategy(@Nonnull ConcurrencyAdjustStrategy strategy) {
        this.strategy = checkNotNull(strategy);
        return this;
    }

    /**
     * default value is {@link #DEFAULT_EVALUATE_PERIOD}
     */
    @CheckReturnValue
    public ConcurrencyAwarePoolBuilder<T> evaluatePeriod(@Nonnull Duration duration){
        this.evaluatePeriod = duration;
        return this;
    }

    /**
     * @param extendThreshold if min concurrency reach this threshold, the pool would extend.
     * @param shrinkThreshold if the second min concurrency below extendThreshold*shrinkThreshold, the pool would shrink.
     */
    @CheckReturnValue
    public ConcurrencyAwarePoolBuilder<T> simpleThresholdStrategy(@Nonnegative int extendThreshold,
            @Nonnegative double shrinkThreshold) {
        return strategy(new SimpleConcurrencyAdjustStrategy(extendThreshold, shrinkThreshold,
                DEFAULT_CONTINUOUS_EXTEND_THRESHOLD, DEFAULT_CONTINUOUS_SHRINK_THRESHOLD));
    }

    /**
     * @param extendThreshold if min concurrency reach this threshold, the pool would extend.
     * @param shrinkThreshold if the second min concurrency below extendThreshold*shrinkThreshold, the pool would shrink.
     */
    @CheckReturnValue
    public ConcurrencyAwarePoolBuilder<T> simpleThresholdStrategy(@Nonnegative int extendThreshold,
            @Nonnegative double shrinkThreshold, @Nonnegative int continuousExtendThreshold,
            @Nonnegative int continuousShrinkThreshold) {
        return strategy(new SimpleConcurrencyAdjustStrategy(extendThreshold, shrinkThreshold,
                continuousExtendThreshold, continuousShrinkThreshold));
    }

    /**
     * @throws IllegalArgumentException when maxSize is smaller than minIdle
     */
    public Pool<T> build(@Nonnull ThrowableSupplier<T, Exception> value) {
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
