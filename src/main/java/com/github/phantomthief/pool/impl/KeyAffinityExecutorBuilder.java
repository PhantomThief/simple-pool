package com.github.phantomthief.pool.impl;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.util.concurrent.MoreExecutors.listeningDecorator;
import static com.google.common.util.concurrent.MoreExecutors.shutdownAndAwaitTermination;
import static java.util.concurrent.TimeUnit.DAYS;

import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;

import com.github.phantomthief.pool.KeyAffinity;
import com.github.phantomthief.pool.KeyAffinityExecutor;
import com.google.common.util.concurrent.ListeningExecutorService;

/**
 * @author w.vela
 * Created on 2018-02-09.
 */
@SuppressWarnings("ResultOfMethodCallIgnored")
public class KeyAffinityExecutorBuilder {

    private final KeyAffinityBuilder<ListeningExecutorService> builder = new KeyAffinityBuilder<>();

    private boolean shutdownAfterClose = true;

    @Nonnull
    public <K> KeyAffinityExecutor<K> build() {
        if (shutdownAfterClose) {
            builder.depose(it -> shutdownAndAwaitTermination(it, 1, DAYS));
        }
        builder.ensure();
        return new ExecutorImpl<>(builder::buildInner);
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

    private class ExecutorImpl<K> extends LazyKeyAffinity<K, ListeningExecutorService> implements
                              KeyAffinityExecutor<K> {

        ExecutorImpl(Supplier<KeyAffinity<K, ListeningExecutorService>> factory) {
            super(factory);
        }
    }
}
