package com.gcanalyzer.aggregation;

import com.microsoft.gctoolkit.aggregator.Aggregates;
import com.microsoft.gctoolkit.aggregator.Aggregator;
import com.microsoft.gctoolkit.aggregator.EventSource;
import com.microsoft.gctoolkit.event.g1gc.G1GCPauseEvent;
import com.microsoft.gctoolkit.event.generational.GenerationalGCPauseEvent;

@Aggregates({EventSource.G1GC, EventSource.GENERATIONAL, EventSource.CMS_UNIFIED, EventSource.TENURED, EventSource.SURVIVOR})
public class GCCycleCountsAggregator extends Aggregator<GCCycleCountsAggregation> {

    public GCCycleCountsAggregator(GCCycleCountsAggregation aggregation) {
        super(aggregation);
        register(G1GCPauseEvent.class, this::process);
        register(GenerationalGCPauseEvent.class, this::process);
    }

    private void process(G1GCPauseEvent event) {
        aggregation().count(event.getGarbageCollectionType(), event.getGCCause());
    }

    private void process(GenerationalGCPauseEvent event) {
        aggregation().count(event.getGarbageCollectionType(), event.getGCCause());
    }
}
