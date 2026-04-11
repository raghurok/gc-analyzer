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
        JavaVirtualMachine jvm = kit.analyze(log);

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
}
