package com.gcanalyzer.aggregation;

import com.microsoft.gctoolkit.aggregator.Aggregation;
import com.microsoft.gctoolkit.aggregator.Collates;
import com.microsoft.gctoolkit.event.GCCause;
import com.microsoft.gctoolkit.event.GarbageCollectionTypes;
import com.microsoft.gctoolkit.time.DateTimeStamp;

/**
 * API surface for recording every GC pause (not just totals). We keep each
 * pause as a discrete point so the diagnostics pass can spot tail outliers
 * and the chart can show the pause timeline.
 */
@Collates(PauseTimeAggregator.class)
public abstract class PauseTimeAggregation extends Aggregation {
    public abstract void recordPause(DateTimeStamp timeStamp, GarbageCollectionTypes gcType, GCCause cause, double durationSeconds);
}
