package com.github.phantomthief.pool.impl;

import static com.google.common.base.MoreObjects.toStringHelper;

import java.time.Duration;
import java.util.Collection;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author w.vela
 * Created on 2017-10-18.
 */
public interface ConcurrencyAdjustStrategy<T> {

    static <T> AdjustResult<T> noChange() {
        return null;
    }

    @Nonnull
    Duration evaluatePeriod();

    /**
     * @return {@link #noChange()} if no changed
     */
    @Nullable
    AdjustResult<T> adjust(@Nonnull Set<CurrentObject<T>> current) throws Throwable;

    class AdjustResult<T> {

        private final Collection<CurrentObject<T>> evict;
        private final int create;

        /**
         * @param evict {@code null} if no item to evict
         */
        public AdjustResult(@Nullable Collection<CurrentObject<T>> evict, int create) {
            this.evict = evict;
            this.create = create;
        }

        Collection<CurrentObject<T>> getEvict() {
            return evict;
        }

        int getCreate() {
            return create;
        }
    }

    class CurrentObject<T> {

        private final T obj;
        private final int currentConcurrency;
        private final long createTime;

        CurrentObject(T obj, int currentConcurrency, long createTime) {
            this.obj = obj;
            this.currentConcurrency = currentConcurrency;
            this.createTime = createTime;
        }

        CurrentObject(ConcurrencyAwarePool<T>.CounterWrapper wrapper) {
            this(wrapper.obj, wrapper.concurrency.intValue(), wrapper.createTime);
        }

        public T getObj() {
            return obj;
        }

        public int getCurrentConcurrency() {
            return currentConcurrency;
        }

        public long getCreateTime() {
            return createTime;
        }

        @Override
        public String toString() {
            return toStringHelper(this) //
                    .add("obj", obj) //
                    .add("currentConcurrency", currentConcurrency) //
                    .add("createTime", createTime) //
                    .toString();
        }
    }
}
