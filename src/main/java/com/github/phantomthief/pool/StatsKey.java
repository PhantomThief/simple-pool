package com.github.phantomthief.pool;

import static com.google.common.base.Preconditions.checkNotNull;

import javax.annotation.Nonnull;

/**
 * @author w.vela
 * Created on 2017-11-15.
 */
public class StatsKey<T> {

    private final Class<T> type;

    public StatsKey(@Nonnull Class<T> type) {
        this.type = checkNotNull(type);
    }

    public Class<T> getType() {
        return type;
    }
}
