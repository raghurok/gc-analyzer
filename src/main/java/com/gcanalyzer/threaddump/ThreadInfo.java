package com.gcanalyzer.threaddump;

import java.util.List;

/**
 * A single thread from a JVM thread dump.
 *
 * @param name        thread name (from the {@code "thread-name"} header line)
 * @param state       thread state (e.g. RUNNABLE, WAITING) — may be null if the dump omits it
 * @param stackFrames stack frames top-to-bottom (most recent call first), as raw
 *                    {@code "com.example.Foo.bar(Foo.java:42)"} strings (no leading {@code "at "})
 */
public record ThreadInfo(String name, String state, List<String> stackFrames) {
}
