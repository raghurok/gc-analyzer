package com.gcanalyzer;

import com.gcanalyzer.aggregation.GCCycleCountsSummary;
import com.gcanalyzer.aggregation.HeapOccupancySummary;
import com.gcanalyzer.aggregation.PauseTimeSummary;
import com.gcanalyzer.report.DiagnosticsReport;
import com.gcanalyzer.report.HeapChartReport;
import com.gcanalyzer.report.JvmSummaryReport;
import com.gcanalyzer.report.MarkerStyle;
import com.gcanalyzer.report.PauseChartReport;
import com.microsoft.gctoolkit.GCToolKit;
import com.microsoft.gctoolkit.io.GCLogFile;
import com.microsoft.gctoolkit.io.SingleGCLogFile;
import com.microsoft.gctoolkit.jvm.JavaVirtualMachine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;

/**
 * Drives a single-file gctoolkit analysis and prints the report sections
 * (summary, heap chart, pause chart, diagnostics) in order.
 */
public class Analyzer {

    private final Path logFile;
    private final int chartWidth;
    private final int chartHeight;
    private final boolean color;
    private final MarkerStyle markers;

    public Analyzer(Path logFile, int chartWidth, int chartHeight, boolean color, MarkerStyle markers) {
        this.logFile = logFile;
        this.chartWidth = chartWidth;
        this.chartHeight = chartHeight;
        this.color = color;
        this.markers = markers;
    }

    public void run(PrintStream out) throws Exception {
        GCLogFile log = new SingleGCLogFile(logFile);
        GCToolKit kit = new GCToolKit();
        kit.loadAggregationsFromServiceLoader();

        // gctoolkit-vertx shuts down its event bus in background threads after
        // analyze() returns.  A known race condition in Vert.x's
        // DeploymentManager can cause "IllegalStateException: Already
        // undeployed" to be printed to stderr — the analysis itself succeeds.
        // We capture stderr during (and briefly after) the analyze() call and
        // filter out that specific noise before replaying anything genuine.
        // See: https://github.com/raghurok/gc-analyzer/issues/1
        JavaVirtualMachine jvm = analyzeWithFilteredStderr(kit, log);

        HeapOccupancySummary heap = jvm.getAggregation(HeapOccupancySummary.class).orElse(null);
        PauseTimeSummary pauses = jvm.getAggregation(PauseTimeSummary.class).orElse(null);
        GCCycleCountsSummary counts = jvm.getAggregation(GCCycleCountsSummary.class).orElse(null);

        new JvmSummaryReport(jvm, counts).print(out);
        out.println();
        new HeapChartReport(heap, chartWidth, chartHeight, markers).print(out);
        out.println();
        new PauseChartReport(pauses, chartWidth, chartHeight, markers).print(out);
        out.println();
        new DiagnosticsReport(jvm, pauses, heap, counts, color).print(out);
    }

    /**
     * Run {@code kit.analyze()} while capturing stderr, then replay only
     * the lines that are NOT part of the known Vert.x "Already undeployed"
     * shutdown noise. A brief pause after analyze() gives background Vert.x
     * threads time to flush their error output through our captured stream
     * before we restore the real stderr.
     */
    private static JavaVirtualMachine analyzeWithFilteredStderr(GCToolKit kit, GCLogFile log) throws Exception {
        PrintStream realErr = System.err;
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errBuffer, true));
        try {
            JavaVirtualMachine jvm = kit.analyze(log);
            // Background Vert.x shutdown threads may still be printing; give
            // them a moment before we stop capturing.
            Thread.sleep(200);
            return jvm;
        } finally {
            System.setErr(realErr);
            replayFilteredStderr(errBuffer.toString(), realErr);
        }
    }

    private static void replayFilteredStderr(String captured, PrintStream err) {
        if (captured.isEmpty()) return;
        boolean suppressing = false;
        for (String line : captured.split("\n")) {
            if (isVertxShutdownNoise(line)) {
                suppressing = true;
                continue;
            }
            // Suppress stack-trace continuation lines ("    at ...") that
            // follow a suppressed exception header.
            if (suppressing && line.trim().startsWith("at ")) {
                continue;
            }
            suppressing = false;
            err.println(line);
        }
    }

    private static boolean isVertxShutdownNoise(String line) {
        return line.contains("Already undeployed")
                || line.contains("DeploymentManager")
                || line.contains("VertxImpl.close")
                || line.contains("VertxChannel.close");
    }
}
