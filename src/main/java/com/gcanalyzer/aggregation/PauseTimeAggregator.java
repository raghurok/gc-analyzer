package com.gcanalyzer.aggregation;

import com.microsoft.gctoolkit.aggregator.Aggregates;
import com.microsoft.gctoolkit.aggregator.Aggregator;
import com.microsoft.gctoolkit.aggregator.EventSource;
import com.microsoft.gctoolkit.event.g1gc.G1GCPauseEvent;
import com.microsoft.gctoolkit.event.generational.GenerationalGCPauseEvent;

@Aggregates({EventSource.G1GC, EventSource.GENERATIONAL, EventSource.CMS_UNIFIED, EventSource.TENURED, EventSource.SURVIVOR})
public class PauseTimeAggregator extends Aggregator<PauseTimeAggregation> {

    public PauseTimeAggregator(PauseTimeAggregation aggregation) {
        super(aggregation);
        register(G1GCPauseEvent.class, this::process);
        register(GenerationalGCPauseEvent.class, this::process);
    }

    private void process(G1GCPauseEvent event) {
        aggregation().recordPause(event.getDateTimeStamp(), event.getGarbageCollectionType(), event.getGCCause(), event.getDuration());
    }

    private void process(GenerationalGCPauseEvent event) {
        aggregation().recordPause(event.getDateTimeStamp(), event.getGarbageCollectionType(), event.getGCCause(), event.getDuration());
    }
}
