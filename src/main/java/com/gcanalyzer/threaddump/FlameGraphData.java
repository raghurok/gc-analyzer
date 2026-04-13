package com.gcanalyzer.threaddump;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a flame graph trie from thread stack traces and serializes it
 * to the JSON format expected by d3-flame-graph.
 *
 * <p>Each thread's stack is reversed (entry point at root, leaf = most recent call)
 * and merged into a trie. Node values represent how many threads include that
 * frame at that depth in the call chain.
 */
public class FlameGraphData {

    private final Node root = new Node("all threads");

    /**
     * Add all threads to the trie.
     */
    public void addAll(List<ThreadInfo> threads) {
        for (ThreadInfo thread : threads) {
            List<String> frames = thread.stackFrames();
            if (frames.isEmpty()) continue;

            // Reverse: bottom of stack (entry point) first
            List<String> reversed = new ArrayList<>(frames);
            java.util.Collections.reverse(reversed);

            Node current = root;
            current.self++;
            for (String frame : reversed) {
                current = current.children.computeIfAbsent(frame, Node::new);
                current.self++;
            }
        }
    }

    /**
     * Serialize the trie to a JSON string for d3-flame-graph.
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        appendNode(sb, root);
        return sb.toString();
    }

    private void appendNode(StringBuilder sb, Node node) {
        sb.append("{\"name\":\"");
        escapeJson(sb, node.name);
        sb.append("\",\"value\":").append(node.self);
        if (!node.children.isEmpty()) {
            sb.append(",\"children\":[");
            boolean first = true;
            for (Node child : node.children.values()) {
                if (!first) sb.append(',');
                appendNode(sb, child);
                first = false;
            }
            sb.append(']');
        }
        sb.append('}');
    }

    private void escapeJson(StringBuilder sb, String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '<'  -> sb.append("\\u003c"); // prevent </script> injection
                default   -> sb.append(c);
            }
        }
    }

    private static class Node {
        final String name;
        int self;
        final Map<String, Node> children = new LinkedHashMap<>();

        Node(String name) {
            this.name = name;
        }
    }
}
