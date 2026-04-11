package com.gcanalyzer.aggregation;

import com.microsoft.gctoolkit.aggregator.Aggregation;
import com.microsoft.gctoolkit.aggregator.Collates;
import com.microsoft.gctoolkit.event.GCCause;
import com.microsoft.gctoolkit.event.GarbageCollectionTypes;

@Collates(GCCycleCountsAggregator.class)
public abstract class GCCycleCountsAggregation extends Aggregation {
    public abstract void count(GarbageCollectionTypes gcType, GCCause cause);
}
