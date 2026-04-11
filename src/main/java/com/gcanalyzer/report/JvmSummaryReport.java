package com.gcanalyzer.report;

import com.gcanalyzer.aggregation.GCCycleCountsSummary;
import com.microsoft.gctoolkit.jvm.JavaVirtualMachine;

import java.io.PrintStream;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JvmSummaryReport {

    private final JavaVirtualMachine jvm;
    private final GCCycleCountsSummary counts;

    public JvmSummaryReport(JavaVirtualMachine jvm, GCCycleCountsSummary counts) {
        this.jvm = jvm;
        this.counts = counts;
    }

    public void print(PrintStream out) {
        out.println("=== JVM Summary ===");
        out.printf("Collector       : %s%n", collectorName());
        out.printf("Log format      : %s%n", jvm.isUnifiedLogging() ? "unified (Java 9+)" : "legacy");
        out.printf("Runtime         : %s%n", formatDuration(jvm.getRuntimeDuration()));
        out.printf("First event     : %s%n", jvm.getTimeOfFirstEvent());
        out.printf("Termination     : %s%n", jvm.getJVMTerminationTime());

        String heapMax = extractHeapMax(jvm.getCommandLine());
        if (heapMax != null) {
            out.printf("Heap max (-Xmx) : %s%n", heapMax);
        }

        if (counts != null && !counts.isEmpty()) {
            out.printf("GC cycles       : %d total%n", counts.total());
            Map<?, Long> typeCounts = counts.getTypeCounts();
            typeCounts.entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .limit(6)
                    .forEach(e -> out.printf("  %-24s %6d%n", e.getKey(), e.getValue()));
        }

        String cmdLine = jvm.getCommandLine();
        if (cmdLine != null && !cmdLine.isBlank()) {
            out.println("Command line    :");
            // wrap long lines
            String prefix = "  ";
            int width = 100;
            for (int i = 0; i < cmdLine.length(); i += width) {
                out.println(prefix + cmdLine.substring(i, Math.min(i + width, cmdLine.length())));
            }
        }
    }

    private String collectorName() {
        if (jvm.isG1GC()) return "G1GC";
        if (jvm.isZGC()) return "ZGC";
        if (jvm.isShenandoah()) return "Shenandoah";
        if (jvm.isCMS()) return "CMS";
        if (jvm.isParallel()) return "Parallel";
        if (jvm.isSerial()) return "Serial";
        return "Unknown";
    }

    private static String formatDuration(double seconds) {
        if (seconds <= 0 || Double.isNaN(seconds)) return "unknown";
        long total = (long) seconds;
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        if (h > 0) return String.format("%dh %dm %ds", h, m, s);
        if (m > 0) return String.format("%dm %ds", m, s);
        return String.format("%.2fs", seconds);
    }

    private static final Pattern XMX = Pattern.compile("-Xmx(\\S+)");

    private static String extractHeapMax(String commandLine) {
        if (commandLine == null) return null;
        Matcher m = XMX.matcher(commandLine);
        return m.find() ? m.group(1) : null;
    }
}
