package com.github.phantomthief.pool.impl;

import java.util.Collection;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author w.vela
 * Created on 2017-10-18.
 */
public interface ConcurrencyAdjustStrategy {

    AdjustResult NO_CHANGE = null;

    /**
     * @return {@link #NO_CHANGE} if no changed
     */
    @Nullable
    AdjustResult adjust(@Nonnull Collection<? extends ConcurrencyInfo> current) throws Throwable;

    class AdjustResult {

        private final Collection<ConcurrencyInfo> evict;
        private final int create;

        /**
         * @param evict {@code null} if no item to evict
         */
        public AdjustResult(@Nullable Collection<ConcurrencyInfo> evict, int create) {
            this.evict = evict;
            this.create = create;
        }

        @Nullable
        Collection<ConcurrencyInfo> getEvict() {
            return evict;
        }

        int getCreate() {
            return create;
        }
    }
}
