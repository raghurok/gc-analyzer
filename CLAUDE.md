# gc-analyzer

Terminal CLI for JVM diagnostics. Two modes:
1. **GC log analysis** — parses a JVM GC log and prints a JVM summary, ASCII charts of heap occupancy and GC pause times, and a rule-based diagnostics section. Built on Microsoft's [gctoolkit](https://github.com/microsoft/gctoolkit). Output is plain stdout, pipeable and SSH-friendly.
2. **Thread dump visualization** — parses a JVM thread dump (jstack/jcmd/kill -3) and generates an interactive HTML flame graph via d3-flame-graph.

## Build & run

```bash
./gradlew build                                 # compile + assemble
./gradlew run --args="samples/gc-sample.log"    # GC log analysis
./gradlew run --args="threaddump samples/threaddump-sample.txt"  # thread dump → flamegraph HTML
./gradlew installDist                           # produce build/install/gc-analyzer/bin/gc-analyzer
./build/install/gc-analyzer/bin/gc-analyzer <log>
./build/install/gc-analyzer/bin/gc-analyzer threaddump <threaddump-file>
```

GC log flags: `<log>` (positional, plain/.gz/.zip), `--width`/`-w` (default 80), `--height`/`-H` (default 12), `--markers`/`-m` (one of `off` (default), `dot`, `cross`, `bullet`), `--no-color`, `--help`, `--version`.

Thread dump flags: `threaddump <file>`, `--format`/`-f` (default `flamegraph`), `--output`/`-o` (default `<input>-flamegraph.html`).

Set `GC_ANALYZER_DEBUG=1` to unmute `java.util.logging` warnings from the gctoolkit parser and Netty/Vert.x — useful when a log isn't producing expected events.

## Project layout

```
build.gradle.kts         Gradle Kotlin DSL; application plugin, deps, JVM flags
settings.gradle.kts      Foojay toolchain resolver plugin
src/main/
├── java/com/gcanalyzer/
│   ├── Main.java                     picocli @Command entry point; JUL suppression
│   ├── Analyzer.java                 orchestrates GCToolKit.analyze() + report ordering
│   ├── report/MarkerStyle.java       enum OFF/DOT/CROSS/BULLET for --markers flag
│   ├── aggregation/                  three Aggregation/Aggregator/Summary triples
│   │   ├── HeapOccupancyAggregation.java      abstract API: addDataPoint(gcType, ts, kb)
│   │   ├── HeapOccupancyAggregator.java       @Aggregates G1GC+GENERATIONAL, registers PauseEvents
│   │   ├── HeapOccupancySummary.java          concrete; holds chronological points + per-GC-type map
│   │   ├── PauseTimeAggregation.java          abstract API: recordPause(ts, type, cause, seconds)
│   │   ├── PauseTimeAggregator.java
│   │   ├── PauseTimeSummary.java              flat list of Pause records + totals + % paused
│   │   ├── GCCycleCountsAggregation.java      abstract API: count(type, cause)
│   │   ├── GCCycleCountsAggregator.java
│   │   └── GCCycleCountsSummary.java          EnumMap<GarbageCollectionTypes,Long> + cause counts
│   ├── report/
│   │   ├── JvmSummaryReport.java     collector name, runtime, GC cycle breakdown, -Xmx from cmdline
│   │   ├── HeapChartReport.java      downsamples KB→MB, linear scale, renders via ASCIIGraph
│   │   ├── PauseChartReport.java     downsamples via MAX per bucket, log₁₀ scale (floor 0.1 ms)
│   │   ├── ChartUtil.java            shared: X-axis footer, log label rewrite, marker overlay
│   │   └── DiagnosticsReport.java    rule-based ✓/⚠/ℹ checks with ANSI color
│   └── threaddump/                   thread dump parsing + flame graph generation
│       ├── ThreadDumpCommand.java    picocli @Command subcommand, orchestrates parse → render
│       ├── ThreadDumpParser.java     line-by-line state machine for jstack format
│       ├── ThreadInfo.java           record: name, state, stackFrames
│       ├── FlameGraphData.java       builds trie from stacks, serializes to d3-flame-graph JSON
│       ├── FlameGraphHtmlRenderer.java  reads HTML template, injects JSON, writes file
│       └── OutputFormat.java         enum (FLAMEGRAPH; extensible for future formats)
└── resources/
    ├── META-INF/services/
    │   └── com.microsoft.gctoolkit.aggregator.Aggregation    SPI: lists the three *Summary classes
    └── templates/
        └── flamegraph.html           HTML template with d3-flame-graph from CDN
samples/                 sample GC logs and thread dumps for smoke testing
```

## How GC log analysis flows

1. `Main` parses args, suppresses gctoolkit/Netty/Vertx `java.util.logging` noise, delegates to `Analyzer`.
2. `Analyzer` opens `SingleGCLogFile` (handles plain/.gz/.zip transparently), calls `GCToolKit.loadAggregationsFromServiceLoader()` which discovers our three `*Summary` classes via `META-INF/services`, then `kit.analyze(log)` runs the parser + aggregators.
3. Each `*Aggregator` registers handlers for `G1GCPauseEvent` / `GenerationalGCPauseEvent` and writes into its aggregation via the abstract `recordX` / `addDataPoint` methods.
4. `Analyzer` pulls out the three summaries via `jvm.getAggregation(XxxSummary.class)` and hands them to the four report classes in order: summary → heap chart → pause chart → diagnostics.
5. Reports print straight to the `PrintStream`.

## How thread dump analysis flows

1. `Main` recognizes the `threaddump` subcommand and delegates to `ThreadDumpCommand`.
2. `ThreadDumpParser` reads the file line-by-line with a state machine, producing `List<ThreadInfo>`. Threads with no stack frames (e.g. GC threads, VM threads) are skipped.
3. `FlameGraphData` reverses each thread's stack (so entry point is at root) and merges them into a trie. Each node's value = number of threads sharing that stack prefix.
4. `FlameGraphHtmlRenderer` loads the `flamegraph.html` template from classpath, injects the trie as JSON, and writes the self-contained HTML file.
5. The HTML loads d3 and d3-flame-graph from jsdelivr CDN at view time.

## Adding a new thread dump output format

1. Add a constant to `OutputFormat.java` (e.g. `SUMMARY`).
2. Create a new renderer class in `threaddump/` (e.g. `ThreadDumpSummaryRenderer.java`).
3. Add a `case` in `ThreadDumpCommand.call()` to dispatch to the new renderer.

## Key dependencies

| Artifact | Version | Purpose |
|---|---|---|
| `com.microsoft.gctoolkit:gctoolkit-api` | 3.7.0 | Aggregation/Aggregator/JVMEvent API |
| `com.microsoft.gctoolkit:gctoolkit-parser` | 3.7.0 | Actual log parsers for each collector |
| `com.microsoft.gctoolkit:gctoolkit-vertx` | 3.7.0 | Event bus gctoolkit uses internally (pulls in Netty) |
| `info.picocli:picocli` | 4.7.6 | CLI arg parsing |
| `com.mitchtalmadge:ascii-data` | 1.4.0 | `ASCIIGraph.fromSeries(...).plot()` for charts |

**Maven coordinate gotcha**: gctoolkit's README/samples say `com.microsoft.gctoolkit:api`, but Maven Central actually publishes `gctoolkit-api` / `gctoolkit-parser` / `gctoolkit-vertx`. Plain `api` / `parser` / `vertx` will 404.

## Toolchain & JVM flags

- `java { toolchain { languageVersion = 25 } }` in `build.gradle.kts`.
- `settings.gradle.kts` applies `org.gradle.toolchains.foojay-resolver-convention:1.0.0` so Gradle can auto-download JDKs. Gradle 9.x requires Foojay 1.0.0+ (0.9.x throws `JvmVendorSpec does not have member field IBM_SEMERU`).
- JVM flags live on `applicationDefaultJvmArgs` (shared by the `run` task and the installDist launcher):
  ```kotlin
  application {
      applicationDefaultJvmArgs = listOf(
          "--enable-native-access=ALL-UNNAMED",
          "--sun-misc-unsafe-memory-access=allow"
      )
  }
  ```
  Both flags suppress Netty's `sun.misc.Unsafe` / native-access warnings on JDK 23+. **Important**: `--sun-misc-unsafe-memory-access` was only added in JDK 23. If the toolchain is ever downgraded below JDK 23, move these flags onto the `startScripts` task instead (`tasks.named<CreateStartScripts>("startScripts") { defaultJvmOpts = ... }`), so the `run` task (which uses the toolchain JDK) doesn't trip over an unknown flag.

## Adding a new aggregation (e.g. allocation rate)

1. Under `src/main/java/com/gcanalyzer/aggregation/`, create three files following the existing triples:
   - `XxxAggregation.java` — abstract class extending `com.microsoft.gctoolkit.aggregator.Aggregation`, annotated `@Collates(XxxAggregator.class)`, declares abstract `recordXxx(...)` methods.
   - `XxxAggregator.java` — extends `Aggregator<XxxAggregation>`, annotated `@Aggregates({EventSource.G1GC, EventSource.GENERATIONAL, ...})`, constructor calls `register(EventClass.class, this::process)` and `process` methods call `aggregation().recordXxx(...)`.
   - `XxxSummary.java` — concrete `extends XxxAggregation` with the data store and `hasWarning()` / `isEmpty()` implementations.
2. Append the FQCN of the `XxxSummary` class to `src/main/resources/META-INF/services/com.microsoft.gctoolkit.aggregator.Aggregation`.
3. Consume it in `Analyzer.run()` via `jvm.getAggregation(XxxSummary.class)` and pass to a new or existing report.

The canonical pattern is documented in `Aggregation.java`'s javadoc in the gctoolkit repo (`FullGCAggregator` / `MaxFullGCPauseTime` example). Keep `@Collates` on the abstract class, not the concrete `*Summary`.

## Chart rendering details

Both charts use `ascii-data`'s `ASCIIGraph` for the line rendering, with three post-processing passes applied in `ChartUtil`:

1. **Marker overlay** (`overlaySampleMarkers`) — **opt-in via `--markers`, default off**. ASCIIGraph draws a continuous smoothed line with box-drawing characters; without markers you can't tell where individual downsampled bucket values sit on the curve, but every marker glyph we've tried trades readability somewhere (heavier glyphs break the eye's sense of line continuity), so the default is no overlay. The overlay computes `row = round((max - series[i]) / (max - min) * (chartHeight - 1))` for each bucket and stamps the caller-supplied glyph at `(firstBodyRow + row, gutter + i)`, only overwriting whitespace or line chars (`╭╮╯╰│─`) so labels and tick markers are preserved. Must be called with the exact same `series` and `chartHeight` values that were passed to `ASCIIGraph`, otherwise marker rows won't line up with the rendered curve. Available glyphs live in `MarkerStyle` — `DOT` (`·`), `CROSS` (`×`), `BULLET` (`●`) — calibrated lightest to heaviest.

2. **Log label rewrite** (`rewriteLogYLabels`) — pause chart only. The pause chart feeds `log10(max(0.1, ms))` to ASCIIGraph, then regex-matches the Y-axis labels (`^\s*(-?\d+(?:\.\d+)?)\s*[┤┼]`) and replaces each with `Math.pow(10, logVal)` formatted to the same field width. Chart body is untouched so column alignment is preserved. **Order matters**: `overlaySampleMarkers` runs first (on the log-space chart with log series), *then* `rewriteLogYLabels` relabels — if you swap the order the marker rows will still be correct but the code is harder to reason about.

3. **X-axis footer** (`xAxisFooter`) — aligned under the chart body. Takes start/end timestamps from the first and last data points (not from `jvm.getTimeOfFirstEvent()`, because GC events start slightly after JVM init).

The pause chart's log floor is `MIN_PAUSE_MS = 0.1` — anything ≤ 0.1 ms collapses to the Y-axis floor. Empty downsample buckets carry-forward the previous bucket's max (not floor-fill), to avoid fake "quiet period" valleys when the chart happens to have a sparse column.

## Adding a new diagnostic check

Edit `report/DiagnosticsReport.java`:
1. Add a method `checkXxx(PrintStream out)` using the existing `ok` / `warn` / `info` helpers.
2. Call it from `print()`.
3. Thresholds live as `private static final` constants at the top of the class.

## Out of scope (do not add without asking)

- Interactive TUI / hover tooltips (deliberately streaming stdout — see conversation history for tradeoff analysis).
- Legacy Java 8 `-XX:+PrintGCDetails` logs (gctoolkit supports them but we don't test that path).
- Rotating multi-file log series (`RotatingGCLogFile`).
- HTML / PNG export.
- Diff-ing two logs side-by-side.

## Event class reference (quick lookup)

Registered events and the fields we extract:

| Event class | Source | How we use it |
|---|---|---|
| `G1GCPauseEvent` | `event.g1gc` | `getHeap()` → `MemoryPoolSummary.getOccupancyAfterCollection()` (KB), `getDuration()` (seconds), `getGarbageCollectionType()`, `getGCCause()` |
| `GenerationalGCPauseEvent` | `event.generational` | same shape, `getHeap()` → post-GC heap occupancy |

`MemoryPoolSummary.getOccupancyAfterCollection()` returns **KB** (not bytes). `JVMEvent.getDuration()` is in **decimal seconds**. Both gotchas are handled in `HeapChartReport` (KB→MB) and `PauseTimeSummary.Pause.durationMillis()` (s→ms).

ZGC and Shenandoah events are *not* currently captured — their concurrent model doesn't map cleanly onto "heap occupancy after a pause" without a separate aggregator. Add support only when a real user log requires it.
