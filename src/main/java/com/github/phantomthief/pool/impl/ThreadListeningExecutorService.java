package com.github.phantomthief.pool.impl;

import static com.google.common.util.concurrent.MoreExecutors.listeningDecorator;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

/**
 * @author w.vela
 * Created on 2018-11-29.
 */
class ThreadListeningExecutorService implements ListeningExecutorService {
    
    private final ThreadPoolExecutor threadPoolExecutor;
    private final ListeningExecutorService wrapped;

    ThreadListeningExecutorService(ThreadPoolExecutor threadPoolExecutor) {
        this.threadPoolExecutor = threadPoolExecutor;
        this.wrapped = listeningDecorator(threadPoolExecutor);
    }

    @Override
    public <T> ListenableFuture<T> submit(Callable<T> task) {
        return wrapped.submit(task);
    }

    @Override
    public ListenableFuture<?> submit(Runnable task) {
        return wrapped.submit(task);
    }

    @Override
    public <T> ListenableFuture<T> submit(Runnable task, T result) {
        return wrapped.submit(task, result);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        return wrapped.invokeAll(tasks);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout,
            TimeUnit unit) throws InterruptedException {
        return wrapped.invokeAll(tasks, timeout, unit);
    }

    @Override
    public void shutdown() {
        wrapped.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return wrapped.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return wrapped.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return wrapped.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return wrapped.awaitTermination(timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        return wrapped.invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return wrapped.invokeAny(tasks, timeout, unit);
    }

    @Override
    public void execute(Runnable command) {
        wrapped.execute(command);
    }

    public int getActiveCount() {
        return threadPoolExecutor.getActiveCount();
    }
}
