package com.github.phantomthief.pool;

import static com.github.phantomthief.pool.KeyAffinityExecutorUtils.RANDOM_THRESHOLD;
import static com.github.phantomthief.pool.KeyAffinityExecutorUtils.executor;
import static com.github.phantomthief.util.MoreReflection.logDeprecated;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Throwables.throwIfUnchecked;
import static com.google.common.util.concurrent.Futures.addCallback;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.function.IntSupplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.github.phantomthief.pool.impl.KeyAffinityExecutorBuilder;
import com.github.phantomthief.util.ThrowableConsumer;
import com.github.phantomthief.util.ThrowableFunction;
import com.github.phantomthief.util.ThrowableRunnable;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.common.util.concurrent.UncheckedExecutionException;

/**
 * @author w.vela
 * Created on 2018-02-09.
 */
public interface KeyAffinityExecutor<K> extends KeyAffinity<K, ListeningExecutorService> {

    int DEFAULT_QUEUE_SIZE = 100;

    @Nonnull
    static KeyAffinityExecutorBuilder newKeyAffinityExecutor() {
        return new KeyAffinityExecutorBuilder();
    }

    @Nonnull
    static <K> KeyAffinityExecutor<K> newSerializingExecutor(int parallelism, String threadName) {
        return newSerializingExecutor(parallelism, DEFAULT_QUEUE_SIZE, threadName);
    }

    /**
     * @param parallelism max concurrency for task submitted.
     * @param queueBufferSize max queue size for every executor, 0 means unbounded queue(DANGEROUS).
     * @param threadName see {@link ThreadFactoryBuilder#setNameFormat(String)}
     */
    @Nonnull
    static <K> KeyAffinityExecutor<K> newSerializingExecutor(int parallelism, int queueBufferSize,
            String threadName) {
        return newKeyAffinityExecutor()
                .count(parallelism)
                .executor(executor(threadName, queueBufferSize))
                .build();
    }

    /**
     * @param parallelism max concurrency for task submitted.
     * @param queueBufferSize max queue size for every executor, 0 means unbounded queue(DANGEROUS).
     * @param threadName see {@link ThreadFactoryBuilder#setNameFormat(String)}
     */
    @Nonnull
    static <K> KeyAffinityExecutor<K> newSerializingExecutor(IntSupplier parallelism, int queueBufferSize,
            String threadName) {
        return newKeyAffinityExecutor()
                .count(parallelism)
                .executor(executor(threadName, queueBufferSize))
                .usingRandom(it -> it > RANDOM_THRESHOLD)
                .build();
    }

    /**
     * should call {@link #execute} or {@link #submit}
     */
    @Override
    default <T, X extends Throwable> T supply(K key,
            @Nonnull ThrowableFunction<ListeningExecutorService, T, X> func) throws X {
        throw new UnsupportedOperationException();
    }

    /**
     * should call {@link #execute} or {@link #submit}
     */
    @Override
    default <X extends Throwable> void run(K key,
            @Nonnull ThrowableConsumer<ListeningExecutorService, X> func) throws X {
        throw new UnsupportedOperationException();
    }

    default <T> ListenableFuture<T> submit(K key, @Nonnull Callable<T> task) {
        checkNotNull(task);

        ListeningExecutorService service = select(key);
        boolean addCallback = false;
        try {
            ListenableFuture<T> future = service.submit(task);
            addCallback(future, new FutureCallback<Object>() {

                @Override
                public void onSuccess(@Nullable Object result) {
                    finishCall(key);
                }

                @Override
                public void onFailure(Throwable t) {
                    finishCall(key);
                }
            }, directExecutor());
            addCallback = true;
            return future;
        } finally {
            if (!addCallback) {
                finishCall(key);
            }
        }
    }

    /**
     * use {@link #executeEx} instead
     */
    @Deprecated
    default ListenableFuture<?> execute(K key, @Nonnull Runnable task) {
        checkNotNull(task);

        logDeprecated("Deprecated calling:KeyAffinityExecutor.execute() at ({}), use executeEx() instead.");

        return submit(key, () -> {
            task.run();
            return null;
        });
    }

    default void executeEx(K key, @Nonnull ThrowableRunnable<Exception> task) {
        checkNotNull(task);

        ListeningExecutorService service = select(key);
        boolean addCallback = false;
        try {
            service.execute(() -> {
                try {
                    task.run();
                } catch (Throwable e) { // pass to uncaught exception handler
                    throwIfUnchecked(e);
                    throw new UncheckedExecutionException(e);
                } finally {
                    finishCall(key);
                }
            });
            addCallback = true;
        } finally {
            if (!addCallback) {
                finishCall(key);
            }
        }
    }

    /**
     * @return {@code} null if not inited
     * @throws IllegalStateException if cannot calc stats
     */
    @Nullable
    KeyAffinityExecutorStats stats();

    /**
     * for stats only, cannot do any operations.
     */
    static Collection<KeyAffinityExecutor<?>> allExecutorsForStats() {
        return KeyAffinityExecutorBuilder.getAllExecutors();
    }
}
