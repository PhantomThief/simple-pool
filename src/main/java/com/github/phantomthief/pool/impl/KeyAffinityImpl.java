package com.github.phantomthief.pool.impl;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import javax.annotation.Nonnull;

import com.github.phantomthief.pool.KeyAffinity;
import com.github.phantomthief.util.ThrowableConsumer;

/**
 * @author w.vela
 * Created on 2018-02-08.
 */
class KeyAffinityImpl<K, V> implements KeyAffinity<K, V> {

    private final List<ValueRef> all;
    private final ThrowableConsumer<V, Exception> deposeFunc;
    private final Map<K, KeyRef> mapping = new ConcurrentHashMap<>();
    private final boolean usingRandom;

    KeyAffinityImpl(@Nonnull Supplier<V> supplier, int count,
            @Nonnull ThrowableConsumer<V, Exception> deposeFunc, boolean usingRandom) {
        this.usingRandom = usingRandom;
        this.all = IntStream.range(0, count) //
                .mapToObj(it -> supplier.get()) //
                .map(ValueRef::new) //
                .collect(toList());
        this.deposeFunc = checkNotNull(deposeFunc);
    }

    @Nonnull
    @Override
    public V select(K key) {
        KeyRef keyRef = mapping.compute(key, (k, v) -> {
            if (v == null) {
                if (usingRandom) {
                    v = new KeyRef(all.get(ThreadLocalRandom.current().nextInt(all.size())));
                } else {
                    v = all.stream() //
                            .min(comparingInt(ValueRef::concurrency)) //
                            .map(KeyRef::new) //
                            .orElseThrow(IllegalStateException::new);
                }
            }
            v.incrConcurrency();
            return v;
        });
        return keyRef.ref();
    }

    @Override
    public void finishCall(K key) {
        mapping.computeIfPresent(key, (k, v) -> {
            if (v.decrConcurrency()) {
                return null;
            } else {
                return v;
            }
        });
    }

    @Override
    public void close() throws Exception {
        synchronized (all) {
            while (all.stream().anyMatch(it -> it.concurrency.get() > 0)) {
                all.wait();
            }
        }
        for (ValueRef ref : all) {
            deposeFunc.accept(ref.obj);
        }
    }

    private class KeyRef {

        private final ValueRef valueRef;
        private final AtomicInteger concurrency = new AtomicInteger();

        KeyRef(ValueRef valueRef) {
            this.valueRef = valueRef;
        }

        void incrConcurrency() {
            concurrency.incrementAndGet();
            valueRef.concurrency.incrementAndGet();
        }

        /**
         * @return {@code true} if no ref by key
         */
        boolean decrConcurrency() {
            int r = concurrency.decrementAndGet();
            int refConcurrency = valueRef.concurrency.decrementAndGet();
            if (refConcurrency <= 0) {
                synchronized (all) {
                    all.notifyAll();
                }
            }
            return r <= 0;
        }

        V ref() {
            return valueRef.obj;
        }
    }

    private class ValueRef {

        private final V obj;
        private final AtomicInteger concurrency = new AtomicInteger();

        ValueRef(V obj) {
            this.obj = obj;
        }

        int concurrency() {
            return concurrency.get();
        }
    }
}
