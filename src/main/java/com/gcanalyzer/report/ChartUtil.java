package com.gcanalyzer.report;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared layout helpers for the ASCII chart reports. */
final class ChartUtil {

    private ChartUtil() {}

    /**
     * Build a one-line X-axis footer aligned with the chart body. The caller
     * passes the gutter width (chars before the chart body) and the body
     * width so the footer's leading spaces and dashes line up with the plot.
     */
    static String xAxisFooter(double startSec, double endSec, int chartBodyWidth, int gutterWidth) {
        String left = String.format("t=%.2fs", startSec);
        String right = String.format("t=%.2fs", endSec);
        int dashes = chartBodyWidth - left.length() - right.length() - 2;
        if (dashes < 1) dashes = 1;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < gutterWidth; i++) sb.append(' ');
        sb.append(left).append(' ');
        for (int i = 0; i < dashes; i++) sb.append('─');
        sb.append(' ').append(right);
        sb.append(String.format("   (Δ %.2fs)", endSec - startSec));
        return sb.toString();
    }

    /**
     * ASCIIGraph has no built-in log scale, so the pause chart feeds it
     * log₁₀(value) and then this helper rewrites the Y-axis labels back to
     * the original ms values. The chart body (bars, spacing, tick markers)
     * is untouched so column alignment is preserved.
     */
    static String rewriteLogYLabels(String logChart) {
        // "   4.50 ┤..." or "   0.00 ┼──..."
        Pattern labelPat = Pattern.compile("^(\\s*)(-?\\d+(?:\\.\\d+)?)(\\s*[┤┼])");
        String[] lines = logChart.split("\n", -1);

        // Determine the label field width from the first matching line so
        // all rewritten labels stay aligned with each other.
        int labelFieldWidth = -1;
        for (String line : lines) {
            Matcher m = labelPat.matcher(line);
            if (m.find()) {
                labelFieldWidth = m.group(1).length() + m.group(2).length();
                break;
            }
        }
        if (labelFieldWidth < 0) return logChart;

        for (int i = 0; i < lines.length; i++) {
            Matcher m = labelPat.matcher(lines[i]);
            if (!m.find()) continue;
            double logVal = Double.parseDouble(m.group(2));
            double original = Math.pow(10, logVal);
            String newLabel = fitLabel(original, labelFieldWidth);
            lines[i] = newLabel + m.group(3) + lines[i].substring(m.end());
        }
        return String.join("\n", lines);
    }

    /**
     * Format {@code v} into exactly {@code width} characters. Picks the most
     * precise decimal representation that still fits; falls back to
     * scientific notation if even integer form overflows.
     */
    private static String fitLabel(double v, int width) {
        String[] patterns = {"%." + 2 + "f", "%." + 1 + "f", "%.0f"};
        for (String p : patterns) {
            String s = String.format(p, v);
            if (s.length() <= width) {
                return String.format("%" + width + "s", s);
            }
        }
        return String.format("%" + width + ".1e", v);
    }

    /** Column index just past the first ┤ or ┼ tick marker in the plot. */
    static int detectGutterWidth(String chart) {
        for (String line : chart.split("\n")) {
            int idx = line.indexOf('┤');
            if (idx < 0) idx = line.indexOf('┼');
            if (idx >= 0) return idx + 1;
        }
        return 0;
    }

    /** Longest rendered row length minus the gutter, i.e. the chart body width. */
    static int detectBodyWidth(String chart, int gutterWidth) {
        int max = 0;
        for (String line : chart.split("\n")) {
            if (line.length() > max) max = line.length();
        }
        return Math.max(0, max - gutterWidth);
    }

    /**
     * Overlay a marker glyph at the exact (row, col) of each downsampled
     * bucket value. ASCIIGraph draws connecting box lines which hide where
     * the individual data points sit; this overlay puts a distinct glyph on
     * top so the reader can see the actual sample positions through the
     * smoothed curve.
     *
     * <p>Opt-in only — callers must check {@code MarkerStyle.enabled()}
     * before invoking this method. The marker glyph itself is chosen by the
     * caller (typically via {@code MarkerStyle.glyph()}) so readability
     * trade-offs stay in one place.
     *
     * @param chart         output of ASCIIGraph.plot()
     * @param series        the exact series that was passed to ASCIIGraph
     * @param gutterWidth   column where the chart body starts (past the ┤ marker)
     * @param chartHeight   the {@code withNumRows} value that was passed to ASCIIGraph
     * @param marker        the glyph to stamp at each plotted bucket position
     */
    static String overlaySampleMarkers(String chart, double[] series, int gutterWidth, int chartHeight, char marker) {
        if (series.length == 0 || chartHeight < 2) return chart;

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (double v : series) {
            if (v < min) min = v;
            if (v > max) max = v;
        }
        if (max - min < 1e-9) return chart; // flat line — nothing meaningful to mark

        String[] lines = chart.split("\n", -1);
        int firstRow = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].indexOf('┤') >= 0 || lines[i].indexOf('┼') >= 0) {
                firstRow = i;
                break;
            }
        }
        if (firstRow < 0) return chart;

        char[][] grid = new char[lines.length][];
        for (int i = 0; i < lines.length; i++) {
            grid[i] = lines[i].toCharArray();
        }

        for (int b = 0; b < series.length; b++) {
            double frac = (max - series[b]) / (max - min);
            int row = firstRow + (int) Math.round(frac * (chartHeight - 1));
            int col = gutterWidth + b;
            if (row < firstRow || row >= firstRow + chartHeight) continue;
            if (row >= grid.length) continue;

            // Pad the row out to `col` if ASCIIGraph didn't render that far.
            if (col >= grid[row].length) {
                char[] extended = new char[col + 1];
                System.arraycopy(grid[row], 0, extended, 0, grid[row].length);
                for (int j = grid[row].length; j < extended.length; j++) extended[j] = ' ';
                grid[row] = extended;
            }
            if (col < 0) continue;

            if (isOverwritableChartCell(grid[row][col])) {
                grid[row][col] = marker;
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < grid.length; i++) {
            sb.append(new String(grid[i]));
            if (i < grid.length - 1) sb.append('\n');
        }
        return sb.toString();
    }

    /** Only stomp on whitespace or box-drawing line chars — never on labels or tick markers. */
    private static boolean isOverwritableChartCell(char c) {
        return c == ' '
                || c == '╭' || c == '╮' || c == '╯' || c == '╰'
                || c == '│' || c == '─';
    }
}
