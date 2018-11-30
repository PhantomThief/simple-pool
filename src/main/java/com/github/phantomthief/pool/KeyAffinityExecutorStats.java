package com.github.phantomthief.pool;

import static com.google.common.base.MoreObjects.toStringHelper;

/**
 * @author w.vela
 * Created on 2018-11-29.
 */
public class KeyAffinityExecutorStats {

    private final int parallelism;
    private final int activeThreadCount;

    public KeyAffinityExecutorStats(int parallelism, int activeThreadCount) {
        this.parallelism = parallelism;
        this.activeThreadCount = activeThreadCount;
    }

    public int getParallelism() {
        return parallelism;
    }

    public int getActiveThreadCount() {
        return activeThreadCount;
    }

    @Override
    public String toString() {
        return toStringHelper(this) //
                .add("parallelism", parallelism) //
                .add("activeThreadCount", activeThreadCount) //
                .toString();
    }
}
