# gc-analyzer

Terminal CLI that parses a JVM GC log and prints a JVM summary, ASCII charts of heap occupancy and GC pause times, and a rule-based diagnostics section. Built on Microsoft's [gctoolkit](https://github.com/microsoft/gctoolkit).

Output is plain stdout, pipeable and SSH-friendly. No TUI, no interactivity — by design.

## Build & run

```bash
./gradlew build                                 # compile + assemble
./gradlew run --args="samples/gc-sample.log"    # run via Gradle
./gradlew installDist                           # produce build/install/gc-analyzer/bin/gc-analyzer
./build/install/gc-analyzer/bin/gc-analyzer <log>
```

Flags: `<log>` (positional, plain/.gz/.zip), `--width`/`-w` (default 80), `--height`/`-H` (default 12), `--no-color`, `--help`, `--version`.

Set `GC_ANALYZER_DEBUG=1` to unmute `java.util.logging` warnings from the gctoolkit parser and Netty/Vert.x — useful when a log isn't producing expected events.

## Project layout

```
build.gradle.kts         Gradle Kotlin DSL; application plugin, deps, JVM flags
settings.gradle.kts      Foojay toolchain resolver plugin
src/main/
├── java/com/gcanalyzer/
│   ├── Main.java                     picocli @Command entry point; JUL suppression
│   ├── Analyzer.java                 orchestrates GCToolKit.analyze() + report ordering
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
│   └── report/
│       ├── JvmSummaryReport.java     collector name, runtime, GC cycle breakdown, -Xmx from cmdline
│       ├── HeapChartReport.java      downsamples KB→MB into chart width, renders via ASCIIGraph
│       ├── PauseChartReport.java     downsamples via MAX per bucket (tail pauses stay visible)
│       └── DiagnosticsReport.java    rule-based ✓/⚠/ℹ checks with ANSI color
└── resources/META-INF/services/
    └── com.microsoft.gctoolkit.aggregator.Aggregation    SPI: lists the three *Summary classes
samples/                 sample GC logs for smoke testing (gc-sample.log, .gz)
```

## How an analysis flows

1. `Main` parses args, suppresses gctoolkit/Netty/Vertx `java.util.logging` noise, delegates to `Analyzer`.
2. `Analyzer` opens `SingleGCLogFile` (handles plain/.gz/.zip transparently), calls `GCToolKit.loadAggregationsFromServiceLoader()` which discovers our three `*Summary` classes via `META-INF/services`, then `kit.analyze(log)` runs the parser + aggregators.
3. Each `*Aggregator` registers handlers for `G1GCPauseEvent` / `GenerationalGCPauseEvent` and writes into its aggregation via the abstract `recordX` / `addDataPoint` methods.
4. `Analyzer` pulls out the three summaries via `jvm.getAggregation(XxxSummary.class)` and hands them to the four report classes in order: summary → heap chart → pause chart → diagnostics.
5. Reports print straight to the `PrintStream`.

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
- JVM flags live **only on the `startScripts` task**, not on `applicationDefaultJvmArgs`:
  ```kotlin
  tasks.named<CreateStartScripts>("startScripts") {
      defaultJvmOpts = listOf(
          "--enable-native-access=ALL-UNNAMED",
          "--sun-misc-unsafe-memory-access=allow"
      )
  }
  ```
  Reason: these flags suppress Netty's `sun.misc.Unsafe` warnings on JDK 23+, but `--sun-misc-unsafe-memory-access` was only added in JDK 23 — older toolchain JDKs reject it. Scoping to `startScripts` means the `run` task is unaffected, and only the generated `bin/gc-analyzer` launcher (which runs under whatever system JDK the user has) carries the flags.

## Adding a new aggregation (e.g. allocation rate)

1. Under `src/main/java/com/gcanalyzer/aggregation/`, create three files following the existing triples:
   - `XxxAggregation.java` — abstract class extending `com.microsoft.gctoolkit.aggregator.Aggregation`, annotated `@Collates(XxxAggregator.class)`, declares abstract `recordXxx(...)` methods.
   - `XxxAggregator.java` — extends `Aggregator<XxxAggregation>`, annotated `@Aggregates({EventSource.G1GC, EventSource.GENERATIONAL, ...})`, constructor calls `register(EventClass.class, this::process)` and `process` methods call `aggregation().recordXxx(...)`.
   - `XxxSummary.java` — concrete `extends XxxAggregation` with the data store and `hasWarning()` / `isEmpty()` implementations.
2. Append the FQCN of the `XxxSummary` class to `src/main/resources/META-INF/services/com.microsoft.gctoolkit.aggregator.Aggregation`.
3. Consume it in `Analyzer.run()` via `jvm.getAggregation(XxxSummary.class)` and pass to a new or existing report.

The canonical pattern is documented in `Aggregation.java`'s javadoc in the gctoolkit repo (`FullGCAggregator` / `MaxFullGCPauseTime` example). Keep `@Collates` on the abstract class, not the concrete `*Summary`.

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
