package com.github.phantomthief.pool;

import javax.annotation.Nonnull;

/**
 * @author w.vela
 * Created on 2017-11-15.
 */
public interface StatsKey<T> {

    @Nonnull
    Class<T> getType();
}
