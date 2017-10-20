package com.github.phantomthief.pool.impl;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Throwables.throwIfUnchecked;
import static com.google.common.util.concurrent.MoreExecutors.shutdownAndAwaitTermination;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static java.lang.Math.min;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.LongAdder;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.phantomthief.failover.util.Weight;
import com.github.phantomthief.pool.Pool;
import com.github.phantomthief.pool.Pooled;
import com.github.phantomthief.pool.impl.ConcurrencyAdjustStrategy.AdjustResult;
import com.github.phantomthief.util.ThrowableConsumer;
import com.github.phantomthief.util.ThrowableSupplier;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

/**
 * 每个对象可以同时被使用n次
 *
 * @author w.vela
 * Created on 06/09/2016.
 */
@ThreadSafe
class ConcurrencyAwarePool<T> implements Pool<T> {

    private static final Logger logger = LoggerFactory.getLogger(ConcurrencyAwarePool.class);

    private final ThrowableConsumer<T, Exception> destroy;

    private final List<CounterWrapper> currentAvailable;

    private final ScheduledExecutorService scheduledExecutor = newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder() //
                    .setNameFormat("concurrency-pool-adjust-%d") //
                    .build());

    private volatile Weight<CounterWrapper> weight;
    private volatile boolean closed = false;

    /**
     * see {@link ConcurrencyAwarePoolBuilder#builder()}
     */
    ConcurrencyAwarePool(ConcurrencyAwarePoolBuilder<T> builder) {
        this.destroy = builder.destroy;

        ThrowableSupplier<T, Exception> factory = builder.factory;
        int minIdle = builder.minIdle;
        int maxSize = builder.maxSize;

        currentAvailable = new ArrayList<>(maxSize);

        for (int i = 0; i < minIdle; i++) {
            try {
                currentAvailable.add(new CounterWrapper(factory.get()));
            } catch (Throwable e) {
                throwIfUnchecked(e);
                throw new RuntimeException(e);
            }
        }
        updateWeight();

        long periodInMs = builder.evaluatePeriod.toMillis();
        ConcurrencyAdjustStrategy strategy = builder.strategy;
        scheduledExecutor.scheduleWithFixedDelay(() -> {
            try {
                if (strategy != null) {
                    AdjustResult adjust = strategy.adjust(currentAvailable);
                    if (adjust == null) {
                        return;
                    }
                    int realToCreate = min(adjust.getCreate(), maxSize - currentAvailable.size());
                    for (int i = 0; i < realToCreate; i++) {
                        currentAvailable.add(new CounterWrapper(factory.get()));
                    }

                    if (adjust.getEvict() != null) {
                        int toRemoveCount = Math.max(0, currentAvailable.size() - minIdle);
                        for (ConcurrencyInfo item : adjust.getEvict()) {
                            if (toRemoveCount <= 0) {
                                break;
                            }
                            if (currentAvailable.removeIf(it -> it == item)) {
                                toRemoveCount--;
                                CounterWrapper.class.cast(item).close();
                            }
                        }
                    }
                }
            } catch (Throwable e) {
                logger.error("", e);
            } finally {
                updateWeight();
            }
        }, periodInMs, periodInMs, MILLISECONDS);
    }

    private void updateWeight() {
        int sum = currentAvailable.stream().mapToInt(CounterWrapper::currentConcurrency).sum();
        Weight<CounterWrapper> thisWeight = new Weight<>();
        for (CounterWrapper item : currentAvailable) {
            thisWeight.add(item, Math.max(1, sum - item.currentConcurrency()));
        }
        weight = thisWeight;
    }

    @Nonnull
    @Override
    public Pooled<T> borrow() {
        checkClosed();
        CounterWrapper counterWrapper;
        try {
            counterWrapper = weight.get();
        } catch (NullPointerException e) {
            throw new IllegalStateException("pool is closed.");
        }
        assert counterWrapper != null;
        counterWrapper.concurrency.increment();
        return counterWrapper;
    }

    private void checkClosed() {
        if (closed || weight == null) {
            throw new IllegalStateException("pool is closed.");
        }
    }

    @Override
    public void returnObject(@Nonnull Pooled<T> pooled) {
        checkNotNull(pooled);
        if (pooled instanceof ConcurrencyAwarePool.CounterWrapper) {
            ((CounterWrapper) pooled).concurrency.decrement();
        } else {
            logger.warn("invalid pooled object:{}", pooled);
        }
    }

    @Override
    public void close() {
        shutdownAndAwaitTermination(scheduledExecutor, 1, DAYS);
        Iterator<CounterWrapper> iterator = currentAvailable.iterator();
        Throwable toThrow = null;
        while (iterator.hasNext()) {
            CounterWrapper wrapper = iterator.next();
            iterator.remove();
            try {
                wrapper.close();
            } catch (Throwable e) {
                toThrow = e;
            }
        }
        closed = true;
        weight = null;
        if (toThrow != null) {
            throwIfUnchecked(toThrow);
            throw new RuntimeException(toThrow);
        }
    }

    class CounterWrapper implements Pooled<T>, AutoCloseable, ConcurrencyInfo {

        final T obj;
        final LongAdder concurrency = new LongAdder();

        CounterWrapper(@Nonnull T obj) {
            this.obj = checkNotNull(obj);
        }

        @Nonnull
        @Override
        public T get() {
            return obj;
        }

        /**
         * would blocking until no using.
         */
        @Override
        public void close() throws Exception {
            while (concurrency.intValue() > 0) {
                sleepUninterruptibly(1, SECONDS);
            }
            if (destroy != null) {
                destroy.accept(obj);
            }
        }

        @Override
        public int currentConcurrency() {
            return concurrency.intValue();
        }
    }
}
