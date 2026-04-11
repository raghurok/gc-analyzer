package com.gcanalyzer.report;

import com.gcanalyzer.aggregation.HeapOccupancySummary;
import com.mitchtalmadge.asciidata.graph.ASCIIGraph;

import java.io.PrintStream;
import java.util.List;

public class HeapChartReport {

    private final HeapOccupancySummary heap;
    private final int width;
    private final int height;

    public HeapChartReport(HeapOccupancySummary heap, int width, int height) {
        this.heap = heap;
        this.width = width;
        this.height = height;
    }

    public void print(PrintStream out) {
        out.println("=== Heap After GC (MB) ===");
        if (heap == null || heap.isEmpty()) {
            out.println("(no heap occupancy data available)");
            return;
        }
        List<HeapOccupancySummary.Point> pts = heap.getAllPointsChronological();
        double[] series = downsample(pts, width);
        String chart = ASCIIGraph.fromSeries(series)
                .withNumRows(Math.max(6, height))
                .plot();
        out.println(chart);
        out.printf("(%d samples, %d points plotted)%n", pts.size(), series.length);
    }

    /** Downsample the chronological occupancy series (KB) to at most {@code maxPoints} buckets (MB). */
    private static double[] downsample(List<HeapOccupancySummary.Point> pts, int maxPoints) {
        int n = pts.size();
        if (n == 0) return new double[0];
        int buckets = Math.min(n, Math.max(10, maxPoints));
        double[] out = new double[buckets];
        int[] counts = new int[buckets];
        for (int i = 0; i < n; i++) {
            int bucket = (int) ((long) i * buckets / n);
            if (bucket >= buckets) bucket = buckets - 1;
            out[bucket] += pts.get(i).occupancyKb / 1024.0; // KB -> MB
            counts[bucket]++;
        }
        // Average per bucket; carry forward previous value for empty buckets to keep a continuous line.
        double last = 0;
        for (int i = 0; i < buckets; i++) {
            if (counts[i] > 0) {
                out[i] = out[i] / counts[i];
                last = out[i];
            } else {
                out[i] = last;
            }
        }
        return out;
    }
}
