package com.gcanalyzer.aggregation;

import com.microsoft.gctoolkit.event.GarbageCollectionTypes;
import com.microsoft.gctoolkit.time.DateTimeStamp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Concrete aggregation holding one time-series (timestamp-seconds → occupancy KB)
 * per GC type, plus a flat chronological list for the overall heap chart.
 */
public class HeapOccupancySummary extends HeapOccupancyAggregation {

    public static final class Point {
        public final double timestampSeconds;
        public final long occupancyKb;
        public final GarbageCollectionTypes gcType;

        Point(double t, long kb, GarbageCollectionTypes type) {
            this.timestampSeconds = t;
            this.occupancyKb = kb;
            this.gcType = type;
        }
    }

    private final List<Point> allPoints = new ArrayList<>();
    private final Map<GarbageCollectionTypes, List<Point>> byType = new EnumMap<>(GarbageCollectionTypes.class);

    @Override
    public synchronized void addDataPoint(GarbageCollectionTypes gcType, DateTimeStamp timeStamp, long heapOccupancyKb) {
        Point p = new Point(timeStamp.getTimeStamp(), heapOccupancyKb, gcType);
        allPoints.add(p);
        byType.computeIfAbsent(gcType, k -> new ArrayList<>()).add(p);
    }

    public synchronized List<Point> getAllPointsChronological() {
        List<Point> copy = new ArrayList<>(allPoints);
        copy.sort((a, b) -> Double.compare(a.timestampSeconds, b.timestampSeconds));
        return copy;
    }

    public synchronized Map<GarbageCollectionTypes, List<Point>> getByType() {
        return Collections.unmodifiableMap(new EnumMap<>(byType));
    }

    @Override
    public boolean hasWarning() {
        return false;
    }

    @Override
    public boolean isEmpty() {
        return allPoints.isEmpty();
    }
}
