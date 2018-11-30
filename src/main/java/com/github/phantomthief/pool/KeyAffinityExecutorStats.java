package com.github.phantomthief.pool;

import static com.google.common.base.MoreObjects.toStringHelper;

import java.util.Collections;
import java.util.List;

/**
 * @author w.vela
 * Created on 2018-11-29.
 */
public class KeyAffinityExecutorStats {

    private final List<SingleThreadPoolStats> stats;

    public KeyAffinityExecutorStats(List<SingleThreadPoolStats> stats) {
        this.stats = stats;
    }

    public List<SingleThreadPoolStats> getThreadPoolStats() {
        return Collections.unmodifiableList(stats);
    }

    @Override
    public String toString() {
        return toStringHelper(this).add("stats", stats).toString();
    }

    public static class SingleThreadPoolStats {

        private final int parallelism;
        private final int activeThreadCount;
        private final int queueSize;

        public SingleThreadPoolStats(int parallelism, int activeThreadCount, int queueSize) {
            this.parallelism = parallelism;
            this.activeThreadCount = activeThreadCount;
            this.queueSize = queueSize;
        }

        public int getParallelism() {
            return parallelism;
        }

        public int getActiveThreadCount() {
            return activeThreadCount;
        }

        public int getQueueSize() {
            return queueSize;
        }

        @Override
        public String toString() {
            return toStringHelper(this)
                    .add("parallelism", parallelism)
                    .add("activeThreadCount", activeThreadCount)
                    .add("queueSize", queueSize)
                    .toString();
        }
    }
}
