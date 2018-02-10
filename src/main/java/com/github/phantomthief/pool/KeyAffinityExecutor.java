package com.github.phantomthief.pool;

import static com.google.common.util.concurrent.Futures.addCallback;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Supplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.github.phantomthief.pool.impl.KeyAffinityExecutorBuilder;
import com.github.phantomthief.util.ThrowableConsumer;
import com.github.phantomthief.util.ThrowableFunction;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

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
     * @param queueBufferSize max queue size for every executor, 0 means unbounded queue.
     * @param threadName see {@link ThreadFactoryBuilder#setNameFormat(String)}
     */
    @Nonnull
    static <K> KeyAffinityExecutor<K> newSerializingExecutor(int parallelism, int queueBufferSize,
            String threadName) {
        return newKeyAffinityExecutor() //
                .count(parallelism) //
                .executor(new Supplier<ExecutorService>() {

                    private final ThreadFactory threadFactory = new ThreadFactoryBuilder() //
                            .setNameFormat(threadName) //
                            .build();

                    @Override
                    public ExecutorService get() {
                        LinkedBlockingQueue<Runnable> queue;
                        if (queueBufferSize > 0) {
                            queue = new LinkedBlockingQueue<Runnable>(queueBufferSize) {

                                @Override
                                public boolean offer(Runnable e) {
                                    try {
                                        put(e);
                                        return true;
                                    } catch (InterruptedException ie) {
                                        Thread.currentThread().interrupt();
                                    }
                                    return false;
                                }
                            };
                        } else {
                            queue = new LinkedBlockingQueue<>();
                        }
                        return new ThreadPoolExecutor(1, 1, 0L, MILLISECONDS, queue, threadFactory);
                    }
                }) //
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

    default <T> ListenableFuture<T> submit(K key, Callable<T> task) {
        ListeningExecutorService service = select(key);
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
        return future;
    }

    default ListenableFuture<?> execute(K key, Runnable task) {
        return submit(key, () -> {
            task.run();
            return null;
        });
    }
}
