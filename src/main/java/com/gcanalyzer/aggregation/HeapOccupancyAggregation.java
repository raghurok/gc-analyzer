package com.gcanalyzer.aggregation;

import com.microsoft.gctoolkit.aggregator.Aggregation;
import com.microsoft.gctoolkit.aggregator.Collates;
import com.microsoft.gctoolkit.event.GarbageCollectionTypes;
import com.microsoft.gctoolkit.time.DateTimeStamp;

/**
 * API surface that {@link HeapOccupancyAggregator} writes into and
 * {@link HeapOccupancySummary} implements for rendering. Captures heap
 * occupancy (in KB) after each collection keyed by GC type.
 */
@Collates(HeapOccupancyAggregator.class)
public abstract class HeapOccupancyAggregation extends Aggregation {
    public abstract void addDataPoint(GarbageCollectionTypes gcType, DateTimeStamp timeStamp, long heapOccupancyKb);
}
