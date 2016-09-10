package com.github.phantomthief.pool;

import java.time.Duration;

import com.github.phantomthief.util.ThrowableConsumer;
import com.github.phantomthief.util.ThrowableFunction;

/**
 * @author w.vela
 * Created on 06/09/2016.
 */
public interface Pool<T> extends AutoCloseable {

    <V, X extends Throwable> V supply(Duration maxWait, ThrowableFunction<T, V, X> function)
            throws X;

    default <V, X extends Throwable> V supply(ThrowableFunction<T, V, X> function) throws X {
        return supply(null, function);
    }

    default <X extends Throwable> void run(Duration maxWait, ThrowableConsumer<T, X> consumer)
            throws X {
        supply(maxWait, obj -> {
            consumer.accept(obj);
            return null;
        });
    }

    default <X extends Throwable> void run(ThrowableConsumer<T, X> consumer) throws X {
        run(null, consumer);
    }

    @Override
    void close();
}
