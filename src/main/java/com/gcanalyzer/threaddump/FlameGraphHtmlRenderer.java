package com.gcanalyzer.threaddump;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Renders a list of parsed threads into a self-contained HTML file
 * with an interactive d3-flame-graph visualization.
 */
public class FlameGraphHtmlRenderer {

    private static final String TEMPLATE_PATH = "/templates/flamegraph.html";
    private static final String DATA_PLACEHOLDER = "/* __FLAMEGRAPH_DATA__ */null";
    private static final String SUBTITLE_PLACEHOLDER = "/* __SUBTITLE__ */";

    public void render(List<ThreadInfo> threads, Path inputFile, Path outputFile) throws IOException {
        String template = loadTemplate();

        FlameGraphData data = new FlameGraphData();
        data.addAll(threads);
        String json = data.toJson();

        String subtitle = threads.size() + " threads from " + inputFile.getFileName();
        String html = template
                .replace(DATA_PLACEHOLDER, json)
                .replace(SUBTITLE_PLACEHOLDER, subtitle);

        Files.writeString(outputFile, html, StandardCharsets.UTF_8);
    }

    private String loadTemplate() throws IOException {
        try (InputStream is = getClass().getResourceAsStream(TEMPLATE_PATH)) {
            if (is == null) {
                throw new IOException("Flamegraph HTML template not found on classpath: " + TEMPLATE_PATH);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
