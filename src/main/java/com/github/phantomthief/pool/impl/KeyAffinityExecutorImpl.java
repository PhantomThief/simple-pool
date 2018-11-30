package com.github.phantomthief.pool.impl;

import static com.github.phantomthief.pool.impl.KeyAffinityExecutorBuilder.ALL_EXECUTORS;

import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.github.phantomthief.pool.KeyAffinity;
import com.github.phantomthief.pool.KeyAffinityExecutor;
import com.github.phantomthief.pool.KeyAffinityExecutorStats;
import com.google.common.util.concurrent.ListeningExecutorService;

/**
 * @author w.vela
 * Created on 2018-11-30.
 */
class KeyAffinityExecutorImpl<K> extends LazyKeyAffinity<K, ListeningExecutorService> implements
                             KeyAffinityExecutor<K> {

    KeyAffinityExecutorImpl(Supplier<KeyAffinity<K, ListeningExecutorService>> factory) {
        super(factory);
    }

    @Override
    public void close() throws Exception {
        try {
            super.close();
        } finally {
            ALL_EXECUTORS.remove(this);
        }
    }

    @Nullable
    @Override
    public KeyAffinityExecutorStats stats() {
        int parallelism = 0;
        int activeCount = 0;
        for (ListeningExecutorService executor : this) {
            if (executor instanceof ThreadListeningExecutorService) {
                parallelism += ((ThreadListeningExecutorService) executor).getMaximumPoolSize();
                activeCount += ((ThreadListeningExecutorService) executor).getActiveCount();
            } else {
                throw new IllegalStateException("cannot get stats for " + this);
            }
        }
        return new KeyAffinityExecutorStats(parallelism, activeCount);
    }
}
