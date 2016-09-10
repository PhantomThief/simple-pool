package com.github.phantomthief.pool.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.time.Duration.ofSeconds;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.github.phantomthief.pool.Pool;

/**
 * @author w.vela
 * Created on 09/09/2016.
 */
public class SimpleCounterPoolBuilder<T> {

    private static final Duration DEFAULT_MAX_WAIT = ofSeconds(30);

    private Consumer<T> destroy;
    private Duration defaultWait;
    private int maxConcurrencyUsingCount;
    private int minIdleCount;
    private int maxCount;
    private long maxIdleTime;

    public Pool<T> build(Supplier<T> factory) {
        checkNotNull(factory);
        ensure();
        return new LazyPool<>(() -> new SimpleCounterPool<>(factory, destroy, defaultWait,
                maxConcurrencyUsingCount, minIdleCount, maxCount, maxIdleTime, maxIdleTime / 2));
    }

    private void ensure() {
        if (destroy == null) {
            destroy = t -> {};
        }
        if (defaultWait == null) {
            defaultWait = DEFAULT_MAX_WAIT;
        }
        checkArgument(maxConcurrencyUsingCount > 0);
    }

    public SimpleCounterPoolBuilder<T> setDestroy(Consumer<T> destroy) {
        checkNotNull(destroy);
        this.destroy = destroy;
        return this;
    }

    public SimpleCounterPoolBuilder<T> setDefaultWait(Duration defaultWait) {
        checkNotNull(defaultWait);
        this.defaultWait = defaultWait;
        return this;
    }

    public SimpleCounterPoolBuilder<T> setMaxConcurrencyUsingCount(int maxConcurrencyUsingCount) {
        this.maxConcurrencyUsingCount = maxConcurrencyUsingCount;
        return this;
    }

    public SimpleCounterPoolBuilder<T> setMinIdleCount(int minIdleCount) {
        checkArgument(minIdleCount > 0);
        this.minIdleCount = minIdleCount;
        return this;
    }

    public SimpleCounterPoolBuilder<T> setMaxCount(int maxCount) {
        checkArgument(maxCount > 0);
        this.maxCount = maxCount;
        return this;
    }

    public SimpleCounterPoolBuilder<T> setMaxIdleTime(Duration maxIdleTime) {
        checkNotNull(maxIdleTime);
        this.maxIdleTime = maxIdleTime.toMillis();
        return this;
    }
}
