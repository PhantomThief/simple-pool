package com.github.phantomthief.pool.impl;

import static com.github.phantomthief.pool.impl.KeyAffinityExecutorForStats.wrapStats;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.util.concurrent.MoreExecutors.listeningDecorator;
import static com.google.common.util.concurrent.MoreExecutors.shutdownAndAwaitTermination;
import static java.util.Collections.unmodifiableCollection;
import static java.util.concurrent.TimeUnit.DAYS;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Supplier;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.github.phantomthief.pool.KeyAffinity;
import com.github.phantomthief.pool.KeyAffinityExecutor;
import com.github.phantomthief.pool.KeyAffinityExecutorStats;
import com.google.common.util.concurrent.ListeningExecutorService;

/**
 * @author w.vela
 * Created on 2018-02-09.
 */
@SuppressWarnings("ResultOfMethodCallIgnored")
public class KeyAffinityExecutorBuilder {

    private static final Map<KeyAffinityExecutor<?>, KeyAffinityExecutor<?>> ALL_EXECUTORS = new ConcurrentHashMap<>();
    private final KeyAffinityBuilder<ListeningExecutorService> builder = new KeyAffinityBuilder<>();

    private boolean shutdownAfterClose = true;

    @Nonnull
    public <K> KeyAffinityExecutor<K> build() {
        if (shutdownAfterClose) {
            builder.depose(it -> shutdownAndAwaitTermination(it, 1, DAYS));
        }
        builder.ensure();
        ExecutorImpl<K> result = new ExecutorImpl<>(builder::buildInner);
        ALL_EXECUTORS.put(result, wrapStats(result));
        return result;
    }

    /**
     * @param value default value if {@code true}
     */
    @CheckReturnValue
    @Nonnull
    public KeyAffinityExecutorBuilder shutdownExecutorAfterClose(boolean value) {
        shutdownAfterClose = value;
        return this;
    }

    /**
     * see {@link KeyAffinityBuilder#usingRandom(boolean)}
     */
    @CheckReturnValue
    @Nonnull
    public KeyAffinityExecutorBuilder usingRandom(boolean value) {
        builder.usingRandom(value);
        return this;
    }

    @CheckReturnValue
    @Nonnull
    public KeyAffinityExecutorBuilder executor(@Nonnull Supplier<ExecutorService> factory) {
        checkNotNull(factory);
        builder.factory(() -> {
            ExecutorService executor = factory.get();
            if (executor instanceof ListeningExecutorService) {
                return (ListeningExecutorService) executor;
            } else if (executor instanceof ThreadPoolExecutor) {
                return new ThreadListeningExecutorService((ThreadPoolExecutor) executor);
            } else {
                return listeningDecorator(executor);
            }
        });
        return this;
    }

    @CheckReturnValue
    @Nonnull
    public KeyAffinityExecutorBuilder count(int count) {
        builder.count(count);
        return this;
    }

    public static Collection<KeyAffinityExecutor<?>> getAllExecutors() {
        return unmodifiableCollection(ALL_EXECUTORS.values());
    }

    private class ExecutorImpl<K> extends LazyKeyAffinity<K, ListeningExecutorService> implements
                              KeyAffinityExecutor<K> {

        ExecutorImpl(Supplier<KeyAffinity<K, ListeningExecutorService>> factory) {
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
}
