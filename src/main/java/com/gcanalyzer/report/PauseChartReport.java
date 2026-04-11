package com.gcanalyzer.report;

import com.gcanalyzer.aggregation.PauseTimeSummary;
import com.mitchtalmadge.asciidata.graph.ASCIIGraph;

import java.io.PrintStream;
import java.util.List;

public class PauseChartReport {

    private final PauseTimeSummary pauses;
    private final int width;
    private final int height;

    public PauseChartReport(PauseTimeSummary pauses, int width, int height) {
        this.pauses = pauses;
        this.width = width;
        this.height = height;
    }

    public void print(PrintStream out) {
        out.println("=== GC Pause Times (ms) ===");
        if (pauses == null || pauses.isEmpty()) {
            out.println("(no pause data available)");
            return;
        }
        List<PauseTimeSummary.Pause> all = pauses.getPauses();
        double[] series = downsampleMax(all, width);
        String chart = ASCIIGraph.fromSeries(series)
                .withNumRows(Math.max(6, height))
                .plot();
        out.println(chart);
        out.printf("Total pauses: %d   Total pause time: %.2fs   %% time paused: %.2f%%   Max pause: %.1f ms%n",
                all.size(),
                pauses.getTotalPauseSeconds(),
                pauses.getPercentPaused(),
                pauses.getMaxPauseSeconds() * 1000.0);
    }

    /**
     * Downsample by taking the MAX of each bucket so tail pauses stay visible
     * rather than getting averaged into the baseline.
     */
    private static double[] downsampleMax(List<PauseTimeSummary.Pause> pts, int maxPoints) {
        int n = pts.size();
        if (n == 0) return new double[0];
        int buckets = Math.min(n, Math.max(10, maxPoints));
        double[] out = new double[buckets];
        for (int i = 0; i < n; i++) {
            int bucket = (int) ((long) i * buckets / n);
            if (bucket >= buckets) bucket = buckets - 1;
            double ms = pts.get(i).durationMillis();
            if (ms > out[bucket]) out[bucket] = ms;
        }
        return out;
    }
}
