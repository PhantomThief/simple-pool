package com.github.phantomthief.pool.impl;

import static com.google.common.util.concurrent.MoreExecutors.shutdownAndAwaitTermination;
import static java.lang.System.currentTimeMillis;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;

import java.time.Duration;
import java.util.Iterator;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.phantomthief.pool.Pool;
import com.github.phantomthief.util.ThrowableFunction;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.common.util.concurrent.UncheckedTimeoutException;

/**
 * 每个对象可以同时被使用n次
 * 对象闲置一段时间后会被回收（基于定时线程）
 *
 * @author w.vela
 * Created on 06/09/2016.
 */
public class SimpleCounterPool<T> implements Pool<T> {

    private static final Logger logger = LoggerFactory.getLogger(SimpleCounterPool.class);
    private final LinkedBlockingDeque<CounterWrapper> idleObjects;
    private final Supplier<T> factory;
    private final Consumer<T> destroy;
    private final Duration defaultWait;
    private final int maxConcurrencyUsingCount;
    private final int minIdleCount;
    private final int maxCount;
    private final long maxIdleTime;
    private final ScheduledExecutorService evictExecutor;
    private final ScheduledFuture<?> evictFuture;
    private volatile boolean closed;

    SimpleCounterPool(Supplier<T> factory, Consumer<T> destroy, Duration defaultWait,
            int maxConcurrencyUsingCount, int minIdleCount, int maxCount, long maxIdleTime,
            long evictCheckPeriod) {

        this.factory = factory;
        this.destroy = destroy;
        this.defaultWait = defaultWait;
        this.maxConcurrencyUsingCount = maxConcurrencyUsingCount;
        this.minIdleCount = minIdleCount;
        this.maxCount = maxCount;
        this.maxIdleTime = maxIdleTime;

        idleObjects = new LinkedBlockingDeque<>();

        evictExecutor = newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setDaemon(true) //
                .setNameFormat("simple-counter-pool-evict-%d") //
                .build());

        evictFuture = evictExecutor.scheduleWithFixedDelay(this::checkEvict, evictCheckPeriod,
                evictCheckPeriod, MILLISECONDS);
    }

    public static <T> SimpleCounterPoolBuilder<T> newBuilder() {
        return new SimpleCounterPoolBuilder<>();
    }

    private void checkEvict() {
        try {
            Iterator<CounterWrapper> iterator = idleObjects.iterator();
            for (int i = 0; iterator.hasNext(); i++) {
                CounterWrapper counterWrapper = iterator.next();
                if (i < minIdleCount) {
                    continue;
                }
                counterWrapper.tryEvict();
            }
        } catch (Throwable e) {
            logger.error("", e);
        }
    }

    private CounterWrapper borrow(Duration maxWait) throws UncheckedTimeoutException {
        if (maxWait == null) {
            maxWait = defaultWait;
        }
        long deadline = currentTimeMillis() + maxWait.toMillis();
        while (true) {
            int[] count = new int[1];
            CounterWrapper result = findAvailable(count);
            if (result != null) {
                return result;
            }
            synchronized (idleObjects) {
                count = new int[1];
                result = findAvailable(count);
                if (result != null) {
                    return result;
                }
                if (maxCount > 0 && count[0] == maxCount) {
                    synchronized (this) {
                        long timeToWait = deadline - currentTimeMillis();
                        if (timeToWait > 0) {
                            try {
                                wait(timeToWait);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            continue;
                        } else {
                            throw new UncheckedTimeoutException();
                        }
                    }
                }
                CounterWrapper counterWrapper = new CounterWrapper(factory.get());
                counterWrapper.using();
                idleObjects.addLast(counterWrapper);
                return counterWrapper;
            }
        }
    }

    private CounterWrapper findAvailable(int[] count) {
        return idleObjects.stream() //
                .peek(obj -> count[0]++) //
                .filter(CounterWrapper::using) //
                .findAny() //
                .orElse(null);
    }

    private void returnObject(CounterWrapper obj) {
        obj.free();
        synchronized (this) {
            notifyAll();
        }
    }

    @Override
    public <V, X extends Throwable> V supply(Duration maxWait, ThrowableFunction<T, V, X> function)
            throws X {
        if (closed) {
            throw new IllegalStateException("pool has been closed.");
        }
        CounterWrapper object = borrow(maxWait);
        try {
            return function.apply(object.getObj());
        } finally {
            returnObject(object);
        }
    }

    @Override
    public void close() {
        evictFuture.cancel(true);
        shutdownAndAwaitTermination(evictExecutor, 1, MINUTES);
        while (!idleObjects.isEmpty()) {
            idleObjects.peekFirst().evict();
        }
        closed = true;
    }

    private class CounterWrapper {

        private final T obj;

        private volatile int usingRef = 0;
        private volatile long lastUsedTime = currentTimeMillis();
        private volatile boolean evicting = false;

        CounterWrapper(T obj) {
            this.obj = obj;
        }

        T getObj() {
            return obj;
        }

        synchronized boolean using() {
            if (evicting) {
                if (usingRef != 0) {
                    logger.warn("invalid counter wrapper stats found.");
                }
                return false;
            }
            if (usingRef < maxConcurrencyUsingCount) {
                usingRef++;
                lastUsedTime = currentTimeMillis();
                return true;
            } else {
                return false;
            }
        }

        synchronized void free() {
            usingRef--;
            notifyAll();
        }

        synchronized void tryEvict() {
            if (usingRef == 0 && currentTimeMillis() - lastUsedTime >= maxIdleTime) {
                doEvict();
            }
        }

        void evict() {
            while (true) {
                synchronized (this) {
                    if (usingRef > 0) {
                        try {
                            wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    } else {
                        doEvict();
                        return;
                    }
                }
            }
        }

        private void doEvict() {
            evicting = true;
            idleObjects.remove(this);
            destroy.accept(obj);
        }
    }
}
