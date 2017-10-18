package com.github.phantomthief.pool.impl;

import static com.github.phantomthief.util.MoreSuppliers.lazy;

import java.util.function.Supplier;

import com.github.phantomthief.pool.Pool;
import com.github.phantomthief.util.MoreSuppliers.CloseableSupplier;
import com.github.phantomthief.util.ThrowableFunction;

/**
 * @author w.vela
 * Created on 09/09/2016.
 */
class LazyPool<T> implements Pool<T> {

    private final CloseableSupplier<Pool<T>> factory;

    LazyPool(Supplier<Pool<T>> factory) {
        this.factory = lazy(factory);
    }

    @Override
    public <V, X extends Throwable> V supply(ThrowableFunction<T, V, X> function) throws X {
        return factory.get().supply(function);
    }

    @Override
    public void close() {
        factory.tryClose(Pool::close);
    }
}
