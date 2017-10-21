package com.github.phantomthief.pool.impl;

import static java.lang.Thread.MIN_PRIORITY;
import static java.util.concurrent.Executors.newCachedThreadPool;

import java.util.concurrent.Executor;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

/**
 * @author w.vela
 * Created on 2017-10-21.
 */
class SharedResource {

    static Executor cleanupExecutor() {
        return LazyHolder.EXECUTOR;
    }

    private static final class LazyHolder {

        private static final Executor EXECUTOR = newCachedThreadPool(new ThreadFactoryBuilder() //
                .setNameFormat("simple-pool-cleanup-%d") //
                .setPriority(MIN_PRIORITY) //
                .build());
    }
}
