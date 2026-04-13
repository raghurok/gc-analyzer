package com.gcanalyzer.threaddump;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a flame graph trie from thread stack traces and serializes it
 * to the JSON format expected by d3-flame-graph.
 *
 * <p>Each thread's stack is reversed (entry point at root, leaf = most recent call)
 * and merged into a trie. Node values represent the <em>self</em> count: threads
 * whose stack terminates at that node. d3-flame-graph computes totals by summing
 * descendants internally.
 *
 * <p>Both the trie construction and JSON serialization use iterative algorithms
 * to handle arbitrarily deep stacks without blowing Java's call stack.
 * The corresponding HTML template pre-computes d3-hierarchy values iteratively
 * in JavaScript to avoid browser call-stack limits as well.
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
            for (String frame : reversed) {
                current = current.children.computeIfAbsent(frame, Node::new);
            }
            // Only the leaf (top of stack) gets the self-count increment
            current.self++;
        }
    }

    /**
     * Serialize the trie to a JSON string for d3-flame-graph.
     * Uses an iterative serializer to avoid Java stack overflow on deep tries.
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        appendIterative(sb, root);
        return sb.toString();
    }

    /**
     * Iterative JSON serialization — avoids stack overflow for deep/wide trees.
     */
    private void appendIterative(StringBuilder sb, Node start) {
        record Frame(Node node, List<Node> childList, int[] idx) {}
        Deque<Frame> stack = new ArrayDeque<>();

        writeNodeOpen(sb, start);
        if (!start.children.isEmpty()) {
            sb.append(",\"children\":[");
            stack.push(new Frame(start, new ArrayList<>(start.children.values()), new int[]{0}));
        } else {
            sb.append('}');
            return;
        }

        while (!stack.isEmpty()) {
            Frame f = stack.peek();
            if (f.idx[0] < f.childList.size()) {
                if (f.idx[0] > 0) sb.append(',');
                Node child = f.childList.get(f.idx[0]);
                f.idx[0]++;
                writeNodeOpen(sb, child);
                if (!child.children.isEmpty()) {
                    sb.append(",\"children\":[");
                    stack.push(new Frame(child, new ArrayList<>(child.children.values()), new int[]{0}));
                } else {
                    sb.append('}');
                }
            } else {
                sb.append("]}");
                stack.pop();
            }
        }
    }

    private void writeNodeOpen(StringBuilder sb, Node node) {
        sb.append("{\"name\":\"");
        escapeJson(sb, node.name);
        sb.append("\",\"value\":").append(node.self);
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

    static class Node {
        String name;
        int self;
        final Map<String, Node> children = new LinkedHashMap<>();

        Node(String name) {
            this.name = name;
        }
    }
}
