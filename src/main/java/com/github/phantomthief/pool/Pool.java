package com.github.phantomthief.pool;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.github.phantomthief.util.ThrowableConsumer;
import com.github.phantomthief.util.ThrowableFunction;

/**
 * @author w.vela
 * Created on 06/09/2016.
 */
@Deprecated
public interface Pool<T> extends AutoCloseable {

    default <V, X extends Throwable> V supply(ThrowableFunction<T, V, X> function) throws X {
        Pooled<T> pooled = borrow();
        try {
            return function.apply(pooled.get());
        } finally {
            returnObject(pooled);
        }
    }

    default <X extends Throwable> void run(ThrowableConsumer<T, X> consumer) throws X {
        supply(obj -> {
            consumer.accept(obj);
            return null;
        });
    }

    /**
     * better use {@link #supply} or {@link #run}
     *
     * @throws IllegalStateException if pool was already closed.
     */
    @Nonnull
    Pooled<T> borrow();

    @Nullable
    <V> V getStats(@Nonnull StatsKey<V> key);

    /**
     * must call exactly once after {@link #borrow} in pair
     */
    void returnObject(@Nonnull Pooled<T> pooled);

    @Override
    void close();
}
