package com.gcanalyzer.aggregation;

import com.microsoft.gctoolkit.event.GCCause;
import com.microsoft.gctoolkit.event.GarbageCollectionTypes;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class GCCycleCountsSummary extends GCCycleCountsAggregation {

    private final Map<GarbageCollectionTypes, Long> typeCounts = new EnumMap<>(GarbageCollectionTypes.class);
    private final Map<GCCause, Long> causeCounts = new HashMap<>();

    @Override
    public synchronized void count(GarbageCollectionTypes gcType, GCCause cause) {
        typeCounts.merge(gcType, 1L, Long::sum);
        if (cause != null) {
            causeCounts.merge(cause, 1L, Long::sum);
        }
    }

    public synchronized Map<GarbageCollectionTypes, Long> getTypeCounts() {
        return Collections.unmodifiableMap(new EnumMap<>(typeCounts));
    }

    public synchronized Map<GCCause, Long> getCauseCounts() {
        return Collections.unmodifiableMap(new HashMap<>(causeCounts));
    }

    public synchronized long total() {
        return typeCounts.values().stream().mapToLong(Long::longValue).sum();
    }

    public synchronized boolean hasSystemGc() {
        return causeCounts.keySet().stream().anyMatch(c -> {
            String name = c == null ? "" : c.name();
            return name.contains("SYSTEM") || name.contains("System");
        });
    }

    @Override
    public boolean hasWarning() {
        return hasSystemGc();
    }

    @Override
    public boolean isEmpty() {
        return typeCounts.isEmpty();
    }
}
