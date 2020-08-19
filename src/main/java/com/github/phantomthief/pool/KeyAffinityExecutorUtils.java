package com.github.phantomthief.pool;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Supplier;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

/**
 * @author w.vela
 * Created on 2020-08-17.
 */
public class KeyAffinityExecutorUtils {

    public static final int RANDOM_THRESHOLD = 20;

    static Supplier<ExecutorService> executor(String threadName, int queueBufferSize) {
        return new Supplier<ExecutorService>() {

            private final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                    .setNameFormat(threadName)
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
        };
    }
}
