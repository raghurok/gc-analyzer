package com.gcanalyzer.aggregation;

import com.microsoft.gctoolkit.event.GCCause;
import com.microsoft.gctoolkit.event.GarbageCollectionTypes;
import com.microsoft.gctoolkit.time.DateTimeStamp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PauseTimeSummary extends PauseTimeAggregation {

    public static final class Pause {
        public final double timestampSeconds;
        public final GarbageCollectionTypes gcType;
        public final GCCause cause;
        public final double durationSeconds;

        Pause(double t, GarbageCollectionTypes type, GCCause cause, double duration) {
            this.timestampSeconds = t;
            this.gcType = type;
            this.cause = cause;
            this.durationSeconds = duration;
        }

        public double durationMillis() {
            return durationSeconds * 1000.0;
        }
    }

    private final List<Pause> pauses = new ArrayList<>();
    private double totalPauseSeconds = 0;

    @Override
    public synchronized void recordPause(DateTimeStamp timeStamp, GarbageCollectionTypes gcType, GCCause cause, double durationSeconds) {
        pauses.add(new Pause(timeStamp.getTimeStamp(), gcType, cause, durationSeconds));
        totalPauseSeconds += durationSeconds;
    }

    public synchronized List<Pause> getPauses() {
        return Collections.unmodifiableList(new ArrayList<>(pauses));
    }

    public synchronized double getTotalPauseSeconds() {
        return totalPauseSeconds;
    }

    public double getPercentPaused() {
        double runtime = estimatedRuntime();
        if (runtime <= 0) return 0;
        return (totalPauseSeconds / runtime) * 100.0;
    }

    public synchronized double getMaxPauseSeconds() {
        return pauses.stream().mapToDouble(p -> p.durationSeconds).max().orElse(0);
    }

    @Override
    public boolean hasWarning() {
        return getMaxPauseSeconds() > 0.5; // 500 ms
    }

    @Override
    public boolean isEmpty() {
        return pauses.isEmpty();
    }
}
