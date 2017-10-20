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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.LongAdder;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private final ScheduledExecutorService scheduledExecutor;
    private final ScheduledFuture<?> scheduledFuture;

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

        ConcurrencyAdjustStrategy strategy = builder.strategy;
        if (strategy != null) {
            long periodInMs = strategy.evaluatePeriod().toMillis();
            scheduledExecutor = newSingleThreadScheduledExecutor(new ThreadFactoryBuilder() //
                    .setNameFormat("concurrency-pool-adjust-%d") //
                    .build());
            scheduledFuture = scheduledExecutor.scheduleWithFixedDelay(() -> {
                try {
                    AdjustResult adjust = strategy.adjust(currentAvailable);
                    if (adjust == null) {
                        return;
                    }
                    int realToCreate = min(adjust.getCreate(), maxSize - currentAvailable.size());
                    for (int i = 0; i < realToCreate; i++) {
                        currentAvailable.add(new CounterWrapper(factory.get()));
                    }

                    if (adjust.getEvict() != null) {
                        adjust.getEvict().stream() //
                                .map(CounterWrapper.class::cast) //
                                .limit(Math.max(0, currentAvailable.size() - minIdle)) //
                                .forEach(wrapper -> {
                                    currentAvailable.removeIf(it -> it == wrapper);
                                    try {
                                        wrapper.close();
                                    } catch (Throwable e) {
                                        logger.error("", e);
                                    }
                                });
                    }
                } catch (Throwable e) {
                    logger.error("", e);
                }
            }, periodInMs, periodInMs, MILLISECONDS);
        } else {
            scheduledFuture = null;
            scheduledExecutor = null;
        }
    }

    @Nonnull
    @Override
    public Pooled<T> borrow() {
        CounterWrapper counterWrapper;
        do {
            checkClosed();
            try {
                counterWrapper = currentAvailable
                        .get(ThreadLocalRandom.current().nextInt(currentAvailable.size()));
                break;
            } catch (IndexOutOfBoundsException e) {
                // ignore, for fast, do it without lock, retry if failed on concurrent modification.
            }
        } while (true);

        counterWrapper.concurrency.increment();
        return counterWrapper;
    }

    private void checkClosed() {
        if (closed || currentAvailable.isEmpty()) {
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
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
            shutdownAndAwaitTermination(scheduledExecutor, 1, DAYS);
        }
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
        if (toThrow != null) {
            throwIfUnchecked(toThrow);
            throw new RuntimeException(toThrow);
        }
    }

    class CounterWrapper implements Pooled<T>, AutoCloseable, ConcurrencyInfo {

        final T obj;
        final LongAdder concurrency = new LongAdder();

        CounterWrapper(T obj) {
            this.obj = obj;
        }

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
