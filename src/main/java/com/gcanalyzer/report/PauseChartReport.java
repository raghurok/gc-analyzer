package com.gcanalyzer.report;

import com.gcanalyzer.aggregation.PauseTimeSummary;
import com.mitchtalmadge.asciidata.graph.ASCIIGraph;

import java.io.PrintStream;
import java.util.List;

public class PauseChartReport {

    /**
     * Floor applied to every pause before the log transform. log₁₀(0) is
     * undefined and sub-millisecond pauses are noise anyway, so we collapse
     * anything ≤ 0.1 ms into the bottom of the axis.
     */
    private static final double MIN_PAUSE_MS = 0.1;

    private final PauseTimeSummary pauses;
    private final int width;
    private final int height;
    private final MarkerStyle markers;

    public PauseChartReport(PauseTimeSummary pauses, int width, int height, MarkerStyle markers) {
        this.pauses = pauses;
        this.width = width;
        this.height = height;
        this.markers = markers;
    }

    public void print(PrintStream out) {
        out.println("=== GC Pause Times (ms, log scale) ===");
        if (pauses == null || pauses.isEmpty()) {
            out.println("(no pause data available)");
            return;
        }
        List<PauseTimeSummary.Pause> all = pauses.getPauses();
        double[] logSeries = downsampleMaxLog(all, width);
        int rows = Math.max(6, height);
        String chart = ASCIIGraph.fromSeries(logSeries)
                .withNumRows(rows)
                .plot();
        int gutter = ChartUtil.detectGutterWidth(chart);
        // Overlay markers on the log-space chart BEFORE relabeling, so
        // marker rows are picked from the same coordinate space ASCIIGraph
        // rendered with.
        if (markers.enabled()) {
            chart = ChartUtil.overlaySampleMarkers(chart, logSeries, gutter, rows, markers.glyph());
        }
        chart = ChartUtil.rewriteLogYLabels(chart);
        out.println(chart);

        double startSec = all.get(0).timestampSeconds;
        double endSec = all.get(all.size() - 1).timestampSeconds;
        int body = ChartUtil.detectBodyWidth(chart, gutter);
        out.println(ChartUtil.xAxisFooter(startSec, endSec, body, gutter));

        out.printf("Total pauses: %d   Total pause time: %.2fs   %% time paused: %.2f%%   Max pause: %.1f ms%n",
                all.size(),
                pauses.getTotalPauseSeconds(),
                pauses.getPercentPaused(),
                pauses.getMaxPauseSeconds() * 1000.0);
        if (markers.enabled()) {
            out.printf("(%c marks each plotted bucket; pauses ≤ %.1f ms collapsed to Y-axis floor)%n",
                    markers.glyph(), MIN_PAUSE_MS);
        } else {
            out.printf("(pauses ≤ %.1f ms collapsed to Y-axis floor)%n", MIN_PAUSE_MS);
        }
    }

    /**
     * Bucket the chronological pauses and take the max pause per bucket (so
     * tail outliers stay visible), then log-transform. Empty buckets
     * carry-forward the previous max — avoids fake "quiet periods" that
     * would show up if we floor-filled instead.
     */
    private static double[] downsampleMaxLog(List<PauseTimeSummary.Pause> pts, int maxPoints) {
        int n = pts.size();
        if (n == 0) return new double[0];
        int buckets = Math.min(n, Math.max(10, maxPoints));
        double[] out = new double[buckets];
        for (int i = 0; i < buckets; i++) out[i] = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < n; i++) {
            int bucket = (int) ((long) i * buckets / n);
            if (bucket >= buckets) bucket = buckets - 1;
            double ms = Math.max(MIN_PAUSE_MS, pts.get(i).durationMillis());
            double logMs = Math.log10(ms);
            if (logMs > out[bucket]) out[bucket] = logMs;
        }
        double prev = Math.log10(MIN_PAUSE_MS);
        for (int i = 0; i < buckets; i++) {
            if (out[i] == Double.NEGATIVE_INFINITY) out[i] = prev;
            else prev = out[i];
        }
        return out;
    }
}
