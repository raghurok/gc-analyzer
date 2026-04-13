package com.gcanalyzer.threaddump;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a JVM thread dump (jstack / kill -3 / jcmd Thread.print output)
 * into a list of {@link ThreadInfo} records.
 */
public class ThreadDumpParser {

    // "thread-name" #id daemon prio=N os_prio=N tid=... nid=... state [addr]
    private static final Pattern THREAD_HEADER = Pattern.compile("^\"(.+?)\".*");

    // java.lang.Thread.State: RUNNABLE
    private static final Pattern THREAD_STATE = Pattern.compile(
            "^\\s+java\\.lang\\.Thread\\.State:\\s+(\\S+)");

    // at com.example.Foo.bar(Foo.java:42)
    private static final Pattern STACK_FRAME = Pattern.compile("^\\s+at\\s+(.+)$");

    /**
     * Parse the given thread dump file.
     *
     * @return list of threads with non-empty stack traces (threads with no frames are skipped)
     */
    public List<ThreadInfo> parse(Path file) throws IOException {
        List<ThreadInfo> threads = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String currentName = null;
            String currentState = null;
            List<String> currentFrames = new ArrayList<>();

            String line;
            while ((line = reader.readLine()) != null) {
                Matcher headerMatcher = THREAD_HEADER.matcher(line);
                if (headerMatcher.matches()) {
                    // Flush previous thread
                    if (currentName != null && !currentFrames.isEmpty()) {
                        threads.add(new ThreadInfo(currentName, currentState,
                                List.copyOf(currentFrames)));
                    }
                    currentName = headerMatcher.group(1);
                    currentState = null;
                    currentFrames.clear();
                    continue;
                }

                if (currentName == null) continue;

                Matcher stateMatcher = THREAD_STATE.matcher(line);
                if (stateMatcher.matches()) {
                    currentState = stateMatcher.group(1);
                    continue;
                }

                Matcher frameMatcher = STACK_FRAME.matcher(line);
                if (frameMatcher.matches()) {
                    currentFrames.add(frameMatcher.group(1));
                }
                // Lock lines (- waiting on, - locked, etc.) are intentionally skipped
            }

            // Flush last thread
            if (currentName != null && !currentFrames.isEmpty()) {
                threads.add(new ThreadInfo(currentName, currentState,
                        List.copyOf(currentFrames)));
            }
        }

        return threads;
    }
}
