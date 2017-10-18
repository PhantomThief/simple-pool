package com.github.phantomthief.pool;

import com.github.phantomthief.util.ThrowableConsumer;
import com.github.phantomthief.util.ThrowableFunction;

/**
 * @author w.vela
 * Created on 06/09/2016.
 */
public interface Pool<T> extends AutoCloseable {

    <V, X extends Throwable> V supply(ThrowableFunction<T, V, X> function) throws X;

    default <X extends Throwable> void run(ThrowableConsumer<T, X> consumer) throws X {
        supply(obj -> {
            consumer.accept(obj);
            return null;
        });
    }

    @Override
    void close();
}
