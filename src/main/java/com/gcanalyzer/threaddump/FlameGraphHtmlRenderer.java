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
    // The JSON is embedded inside a JS single-quoted string: JSON.parse('...')
    // so the placeholder includes the surrounding quotes from the template.
    private static final String DATA_PLACEHOLDER = "/* __FLAMEGRAPH_DATA__ */";
    private static final String SUBTITLE_PLACEHOLDER = "/* __SUBTITLE__ */";

    public void render(List<ThreadInfo> threads, Path inputFile, Path outputFile) throws IOException {
        String template = loadTemplate();

        FlameGraphData data = new FlameGraphData();
        data.addAll(threads);
        String json = data.toJson();

        // The JSON is placed inside a JS single-quoted string: JSON.parse('...')
        // so we must escape single quotes and backslashes for that context.
        // Backslashes in the JSON (from escapeJson) are already doubled for JSON;
        // we need to double them again for the JS string literal, and escape
        // any single quotes that appear in frame names.
        String jsStringContent = json.replace("\\", "\\\\").replace("'", "\\'");

        String subtitle = threads.size() + " threads from " + inputFile.getFileName();
        String html = template
                .replace(DATA_PLACEHOLDER, jsStringContent)
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
