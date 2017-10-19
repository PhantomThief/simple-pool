package com.github.phantomthief.pool.impl;

import static com.github.phantomthief.util.MoreSuppliers.lazy;

import java.util.function.Supplier;

import com.github.phantomthief.pool.Pool;
import com.github.phantomthief.pool.Pooled;
import com.github.phantomthief.util.MoreSuppliers.CloseableSupplier;

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
    public Pooled<T> borrow() {
        return factory.get().borrow();
    }

    @Override
    public void returnObject(Pooled<T> pooled) {
        factory.get().returnObject(pooled);
    }

    @Override
    public void close() {
        factory.tryClose(Pool::close);
    }
}
