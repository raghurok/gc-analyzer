package com.gcanalyzer.aggregation;

import com.microsoft.gctoolkit.aggregator.Aggregates;
import com.microsoft.gctoolkit.aggregator.Aggregator;
import com.microsoft.gctoolkit.aggregator.EventSource;
import com.microsoft.gctoolkit.event.MemoryPoolSummary;
import com.microsoft.gctoolkit.event.g1gc.G1GCPauseEvent;
import com.microsoft.gctoolkit.event.generational.GenerationalGCPauseEvent;

/**
 * Extracts post-GC heap occupancy from every pause event produced by the
 * G1 and generational (Serial/Parallel/CMS) parsers. ZGC/Shenandoah are not
 * captured here — their concurrent model doesn't map cleanly onto "occupancy
 * after a pause" and they'd need their own aggregator if we wanted to cover
 * them.
 */
@Aggregates({EventSource.G1GC, EventSource.GENERATIONAL, EventSource.CMS_UNIFIED, EventSource.TENURED, EventSource.SURVIVOR})
public class HeapOccupancyAggregator extends Aggregator<HeapOccupancyAggregation> {

    public HeapOccupancyAggregator(HeapOccupancyAggregation aggregation) {
        super(aggregation);
        register(G1GCPauseEvent.class, this::process);
        register(GenerationalGCPauseEvent.class, this::process);
    }

    private void process(G1GCPauseEvent event) {
        MemoryPoolSummary heap = event.getHeap();
        if (heap != null) {
            aggregation().addDataPoint(event.getGarbageCollectionType(), event.getDateTimeStamp(), heap.getOccupancyAfterCollection());
        }
    }

    private void process(GenerationalGCPauseEvent event) {
        MemoryPoolSummary heap = event.getHeap();
        if (heap != null) {
            aggregation().addDataPoint(event.getGarbageCollectionType(), event.getDateTimeStamp(), heap.getOccupancyAfterCollection());
        }
    }
}
