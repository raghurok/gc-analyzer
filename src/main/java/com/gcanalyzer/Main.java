package com.gcanalyzer;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

@Command(
        name = "gc-analyzer",
        mixinStandardHelpOptions = true,
        version = "gc-analyzer 0.1.0",
        description = "Parse a JVM GC log and print a summary, ASCII charts, and diagnostics."
)
public class Main implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "<log>",
            description = "Path to a GC log file (plain text, .gz, or .zip). Unified logging (Java 9+) is supported.")
    private Path logFile;

    @Option(names = {"-w", "--width"}, description = "Chart width in characters (default: ${DEFAULT-VALUE}).")
    private int width = 80;

    @Option(names = {"-H", "--height"}, description = "Chart height in rows (default: ${DEFAULT-VALUE}).")
    private int height = 12;

    @Option(names = "--no-color", description = "Disable ANSI color in the diagnostics section.")
    private boolean noColor = false;

    @Override
    public Integer call() {
        if (!Files.exists(logFile)) {
            System.err.println("Error: file not found: " + logFile);
            return 2;
        }
        if (!Files.isRegularFile(logFile)) {
            System.err.println("Error: not a regular file: " + logFile);
            return 2;
        }
        try {
            new Analyzer(logFile, width, height, !noColor).run(System.out);
            return 0;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            if (System.getenv("GC_ANALYZER_DEBUG") != null) {
                e.printStackTrace(System.err);
            }
            return 1;
        }
    }

    public static void main(String[] args) {
        // gctoolkit's parsers emit java.util.logging WARNINGs for events they
        // don't classify (e.g. "G1GC Event type is undefined (null)"). They
        // are internal parser notes, not user-actionable, and they pollute
        // the report on stderr. Silence the parser package unless the user
        // explicitly opts in via GC_ANALYZER_DEBUG.
        if (System.getenv("GC_ANALYZER_DEBUG") == null) {
            Logger.getLogger("com.microsoft.gctoolkit.parser").setLevel(Level.SEVERE);
            Logger.getLogger("com.microsoft.gctoolkit").setLevel(Level.SEVERE);
            Logger.getLogger("io.netty").setLevel(Level.SEVERE);
            Logger.getLogger("io.vertx").setLevel(Level.SEVERE);
        }
        int exit = new CommandLine(new Main()).execute(args);
        System.exit(exit);
    }
}
