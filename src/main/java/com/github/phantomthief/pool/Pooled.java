package com.github.phantomthief.pool;

import java.util.function.Supplier;

/**
 * @author w.vela
 * Created on 2017-10-19.
 */
public interface Pooled<T> extends Supplier<T> {

    @Override
    T get();
}
