package com.github.phantomthief.pool.impl;

import static com.github.phantomthief.pool.impl.SharedResource.cleanupExecutor;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Throwables.throwIfUnchecked;
import static com.google.common.util.concurrent.MoreExecutors.shutdownAndAwaitTermination;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static java.lang.Math.min;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.LongAdder;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.phantomthief.failover.util.Weight;
import com.github.phantomthief.pool.Pool;
import com.github.phantomthief.pool.Pooled;
import com.github.phantomthief.pool.StatsKey;
import com.github.phantomthief.pool.impl.ConcurrencyAdjustStrategy.AdjustResult;
import com.github.phantomthief.util.ThrowableConsumer;
import com.github.phantomthief.util.ThrowableSupplier;
import com.google.common.base.Supplier;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

/**
 * @author w.vela
 * Created on 06/09/2016.
 */
@ThreadSafe
public class ConcurrencyAwarePool<T> implements Pool<T> {

    private static final Logger logger = LoggerFactory.getLogger(ConcurrencyAwarePool.class);

    public static final StatsKey<Integer> CURRENT_COUNT = new StatsKey<>(Integer.class);

    private final ThrowableConsumer<T, Exception> destroy;

    private final List<CounterWrapper> currentAvailable;

    private final ScheduledExecutorService scheduledExecutor = newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder() //
                    .setNameFormat("concurrency-pool-adjust-%d") //
                    .build());

    private final Map<StatsKey<?>, Supplier<?>> stats;

    /**
     * see {@link ConcurrencyAwarePool#builder()}
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
            List<CounterWrapper> toClosed = null;
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
                                if (toClosed == null) {
                                    toClosed = new ArrayList<>();
                                }
                                toClosed.add(CounterWrapper.class.cast(item));
                            }
                        }
                    }
                }
            } catch (Throwable e) {
                logger.error("", e);
            } finally {
                updateWeight(); // after this call, no more new requests would be reached.
                closePending(toClosed);
            }
        }, periodInMs, periodInMs, MILLISECONDS);

        stats = buildStats();
    }

    // weight is always non-null except for pool is closed.
    private volatile Weight<CounterWrapper> weight;
    private volatile boolean closing = false;

    private Map<StatsKey<?>, Supplier<?>> buildStats() {
        Map<StatsKey<?>, Supplier<?>> map = new HashMap<>();
        map.put(CURRENT_COUNT, currentAvailable::size);
        return map;
    }

    private void closePending(List<CounterWrapper> toClosed) {
        if (toClosed == null) {
            return;
        }
        for (CounterWrapper item : toClosed) {
            cleanupExecutor().execute(() -> {
                try {
                    item.close();
                } catch (Throwable e) {
                    logger.error("", e);
                }
            });
        }
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
        if (closing) {
            throw new IllegalStateException("pool is closed.");
        }
        CounterWrapper counterWrapper;
        try {
            counterWrapper = weight.get();
        } catch (NullPointerException e) {
            throw new IllegalStateException("pool is closed.");
        }
        assert counterWrapper != null;
        counterWrapper.enter();
        return counterWrapper;
    }

    @Override
    public <V> V getStats(@Nonnull StatsKey<V> key) {
        checkNotNull(key);
        return ofNullable(stats.get(key)) //
                .map(Supplier::get) //
                .map(obj -> key.getType().cast(obj)) //
                .orElse(null);
    }

    @Override
    public void returnObject(@Nonnull Pooled<T> pooled) {
        checkNotNull(pooled);
        if (pooled instanceof ConcurrencyAwarePool.CounterWrapper) {
            ((CounterWrapper) pooled).leave();
        } else {
            logger.warn("invalid pooled object:{}", pooled);
        }
    }

    @Override
    public void close() {
        closing = true;
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
        weight = null;
        if (toThrow != null) {
            throwIfUnchecked(toThrow);
            throw new RuntimeException(toThrow);
        }
    }

    @CheckReturnValue
    public static <T> ConcurrencyAwarePoolBuilder<T> builder() {
        return new ConcurrencyAwarePoolBuilder<>();
    }

    private class CounterWrapper implements Pooled<T>, AutoCloseable, ConcurrencyInfo {

        private final T obj;
        private final LongAdder concurrency = new LongAdder();

        private volatile boolean closing = false;

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
            closing = true;
            synchronized (concurrency) {
                while (concurrency.intValue() > 0) {
                    concurrency.wait(MINUTES.toMillis(1));
                }
            }
            if (destroy != null) {
                // sleep for one more second for safety.
                sleepUninterruptibly(1, SECONDS);
                destroy.accept(obj);
            }
        }

        @Override
        public int currentConcurrency() {
            return concurrency.intValue();
        }

        private void enter() {
            concurrency.increment();
        }

        private void leave() {
            concurrency.decrement();
            if (closing) {
                synchronized (concurrency) {
                    concurrency.notifyAll();
                }
            }
        }
    }
}
