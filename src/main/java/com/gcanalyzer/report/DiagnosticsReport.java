package com.gcanalyzer.report;

import com.gcanalyzer.aggregation.GCCycleCountsSummary;
import com.gcanalyzer.aggregation.HeapOccupancySummary;
import com.gcanalyzer.aggregation.PauseTimeSummary;
import com.microsoft.gctoolkit.jvm.JavaVirtualMachine;

import java.io.PrintStream;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Rule-based diagnostics. Each check emits a ✓ (ok), ⚠ (warn), or ✗ (issue).
 * Intentionally simple — this isn't trying to replace a profiler, just to
 * flag the obvious smells a human would want highlighted on first read.
 */
public class DiagnosticsReport {

    private static final double LONG_PAUSE_THRESHOLD_SEC = 0.5;        // 500 ms
    private static final double HIGH_PAUSE_PERCENT_THRESHOLD = 5.0;    // %
    private static final double LEAK_GROWTH_THRESHOLD = 1.5;           // last-quartile avg / first-quartile avg

    private final JavaVirtualMachine jvm;
    private final PauseTimeSummary pauses;
    private final HeapOccupancySummary heap;
    private final GCCycleCountsSummary counts;
    private final boolean color;

    public DiagnosticsReport(JavaVirtualMachine jvm, PauseTimeSummary pauses, HeapOccupancySummary heap, GCCycleCountsSummary counts, boolean color) {
        this.jvm = jvm;
        this.pauses = pauses;
        this.heap = heap;
        this.counts = counts;
        this.color = color;
    }

    public void print(PrintStream out) {
        out.println("=== Diagnostics ===");
        checkLongPauses(out);
        checkPausePercent(out);
        checkHeapGrowth(out);
        checkSystemGc(out);
        checkFullGcs(out);
    }

    private void checkLongPauses(PrintStream out) {
        if (pauses == null || pauses.isEmpty()) {
            info(out, "no pause data to evaluate");
            return;
        }
        List<PauseTimeSummary.Pause> longOnes = pauses.getPauses().stream()
                .filter(p -> p.durationSeconds > LONG_PAUSE_THRESHOLD_SEC)
                .sorted(Comparator.comparingDouble((PauseTimeSummary.Pause p) -> p.durationSeconds).reversed())
                .toList();
        if (longOnes.isEmpty()) {
            ok(out, String.format("no pauses exceeded %.0f ms (max: %.1f ms)",
                    LONG_PAUSE_THRESHOLD_SEC * 1000, pauses.getMaxPauseSeconds() * 1000));
        } else {
            warn(out, String.format("%d pause(s) exceeded %.0f ms",
                    longOnes.size(), LONG_PAUSE_THRESHOLD_SEC * 1000));
            int shown = Math.min(5, longOnes.size());
            for (int i = 0; i < shown; i++) {
                PauseTimeSummary.Pause p = longOnes.get(i);
                out.printf("    • %8.1f ms  t+%.1fs  %s  (%s)%n",
                        p.durationMillis(), p.timestampSeconds, p.gcType, p.cause);
            }
        }
    }

    private void checkPausePercent(PrintStream out) {
        if (pauses == null || pauses.isEmpty()) return;
        double pct = pauses.getPercentPaused();
        if (pct > HIGH_PAUSE_PERCENT_THRESHOLD) {
            warn(out, String.format("application paused %.2f%% of runtime (threshold %.0f%%)", pct, HIGH_PAUSE_PERCENT_THRESHOLD));
        } else {
            ok(out, String.format("application paused only %.2f%% of runtime", pct));
        }
    }

    private void checkHeapGrowth(PrintStream out) {
        if (heap == null || heap.isEmpty()) return;
        List<HeapOccupancySummary.Point> pts = heap.getAllPointsChronological();
        if (pts.size() < 20) {
            info(out, "heap trend: not enough data points (" + pts.size() + ")");
            return;
        }
        int q = pts.size() / 4;
        double firstAvg = pts.subList(0, q).stream().mapToLong(p -> p.occupancyKb).average().orElse(0);
        double lastAvg = pts.subList(pts.size() - q, pts.size()).stream().mapToLong(p -> p.occupancyKb).average().orElse(0);
        if (firstAvg <= 0) return;
        double ratio = lastAvg / firstAvg;
        if (ratio > LEAK_GROWTH_THRESHOLD) {
            warn(out, String.format("post-GC heap grew %.1fx over run (%.0f MB → %.0f MB) — possible leak",
                    ratio, firstAvg / 1024.0, lastAvg / 1024.0));
        } else {
            ok(out, String.format("post-GC heap is stable (%.0f MB → %.0f MB)", firstAvg / 1024.0, lastAvg / 1024.0));
        }
    }

    private void checkSystemGc(PrintStream out) {
        if (counts == null || counts.isEmpty()) return;
        if (counts.hasSystemGc()) {
            long n = counts.getCauseCounts().entrySet().stream()
                    .filter(e -> {
                        String name = e.getKey() == null ? "" : e.getKey().name();
                        return name.contains("SYSTEM") || name.contains("System");
                    })
                    .mapToLong(Map.Entry::getValue)
                    .sum();
            warn(out, String.format("%d System.gc() call(s) detected — consider -XX:+DisableExplicitGC", n));
        } else {
            ok(out, "no System.gc() calls");
        }
    }

    private void checkFullGcs(PrintStream out) {
        if (counts == null || counts.isEmpty()) return;
        long fullGcs = counts.getTypeCounts().entrySet().stream()
                .filter(e -> {
                    String n = e.getKey().name();
                    return n.contains("Full") || n.contains("FULL");
                })
                .mapToLong(Map.Entry::getValue)
                .sum();
        if (fullGcs == 0) {
            ok(out, "no Full GCs");
        } else {
            warn(out, fullGcs + " Full GC(s) — these are stop-the-world and usually indicate heap pressure");
        }
    }

    // ---- formatting helpers ----

    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String RESET = "\u001B[0m";

    private void ok(PrintStream out, String msg) {
        out.println("  " + c(GREEN) + "✓" + c(RESET) + " " + msg);
    }

    private void warn(PrintStream out, String msg) {
        out.println("  " + c(YELLOW) + "⚠" + c(RESET) + " " + msg);
    }

    private void info(PrintStream out, String msg) {
        out.println("  " + c(BLUE) + "ℹ" + c(RESET) + " " + msg);
    }

    private String c(String code) {
        return color ? code : "";
    }
}
