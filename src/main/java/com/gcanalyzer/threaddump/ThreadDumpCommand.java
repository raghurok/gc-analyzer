package com.gcanalyzer.threaddump;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "threaddump",
        description = "Parse a JVM thread dump and visualize it.",
        mixinStandardHelpOptions = true
)
public class ThreadDumpCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "<file>",
            description = "Path to a JVM thread dump file (jstack / jcmd Thread.print output).")
    private Path inputFile;

    @Option(names = {"-f", "--format"}, paramLabel = "<format>",
            description = "Output format: ${COMPLETION-CANDIDATES}. Default: ${DEFAULT-VALUE}.")
    private OutputFormat format = OutputFormat.FLAMEGRAPH;

    @Option(names = {"-o", "--output"}, paramLabel = "<file>",
            description = "Output file path. Default: <input>-flamegraph.html in current directory.")
    private Path outputFile;

    @Override
    public Integer call() {
        if (!Files.exists(inputFile)) {
            System.err.println("Error: file not found: " + inputFile);
            return 2;
        }
        if (!Files.isRegularFile(inputFile)) {
            System.err.println("Error: not a regular file: " + inputFile);
            return 2;
        }

        try {
            List<ThreadInfo> threads = new ThreadDumpParser().parse(inputFile);
            if (threads.isEmpty()) {
                System.err.println("Error: no threads with stack traces found in " + inputFile);
                return 1;
            }

            Path out = resolveOutputFile();

            switch (format) {
                case FLAMEGRAPH -> new FlameGraphHtmlRenderer().render(threads, inputFile, out);
            }

            System.err.println("Wrote " + format.name().toLowerCase() + " (" + threads.size()
                    + " threads) to " + out);
            return 0;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            if (System.getenv("GC_ANALYZER_DEBUG") != null) {
                e.printStackTrace(System.err);
            }
            return 1;
        }
    }

    private Path resolveOutputFile() {
        if (outputFile != null) return outputFile;
        String baseName = inputFile.getFileName().toString().replaceFirst("\\.[^.]+$", "");
        String suffix = switch (format) {
            case FLAMEGRAPH -> "-flamegraph.html";
        };
        return inputFile.toAbsolutePath().getParent().resolve(baseName + suffix);
    }
}
