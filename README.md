# gc-analyzer

A terminal CLI that parses a JVM garbage-collection log and prints a summary, two ASCII charts (heap occupancy and GC pause times), and a rule-based diagnostics block — all to plain stdout. Pipeable, SSH-friendly, no GUI.

Built on Microsoft's [gctoolkit](https://github.com/microsoft/gctoolkit), so it understands whatever `gctoolkit` understands — G1, Parallel, Serial, CMS, ZGC, Shenandoah — with unified logging (Java 9+) as the primary target.

## What it gives you

- **JVM summary** — collector, runtime, first/last event timestamps, GC cycle count broken down by type, command line if present, `-Xmx` parsed out.
- **Heap-after-GC chart** — linear Y-axis in MB, one plotted point per downsampled time bucket, with a time-range footer under the body.
- **GC pause-time chart** — log₁₀ Y-axis (so tail pauses and baseline pauses are both visible in the same view), pauses ≤ 0.1 ms collapsed to the floor.
- **Diagnostics** — green ✓ / yellow ⚠ rule checks for long pauses (>500 ms), overall % time paused, post-GC heap growth (likely leak signal), `System.gc()` calls, and Full GCs.

## Requirements

- macOS or Linux terminal with UTF-8 and box-drawing glyph support (any modern terminal works — Terminal.app, iTerm2, Alacritty, tmux).
- A JDK on your `PATH` for running the installed launcher. The build itself uses a Gradle toolchain (auto-downloaded via the Foojay resolver) so your system JDK version doesn't matter for `./gradlew build`.

## Build and run

```bash
# one-shot run
./gradlew run --args="path/to/gc.log"

# build a self-contained launcher, then use it directly
./gradlew installDist
./build/install/gc-analyzer/bin/gc-analyzer path/to/gc.log

# accepts plain, gzip, or zip
./build/install/gc-analyzer/bin/gc-analyzer path/to/gc.log.gz
```

The `installDist` launcher at `build/install/gc-analyzer/bin/gc-analyzer` is a plain shell script — symlink it into your `$PATH` if you want a global `gc-analyzer` command.

## Options

```
Usage: gc-analyzer [-hV] [--no-color] [-H=<height>] [-m=<style>] [-w=<width>] <log>

Parse a JVM GC log and print a summary, ASCII charts, and diagnostics.

      <log>               Path to a GC log file (plain text, .gz, or .zip).
                            Unified logging (Java 9+) is supported.
  -h, --help              Show this help message and exit.
  -H, --height=<height>   Chart height in rows (default: 12).
  -m, --markers=<style>   Overlay a glyph at each plotted sample point. One of:
                            off, dot, cross, bullet. Default: off.
      --no-color          Disable ANSI color in the diagnostics section.
  -V, --version           Print version information and exit.
  -w, --width=<width>     Chart width in characters (default: 80).
```

Set `GC_ANALYZER_DEBUG=1` if you want to unmute gctoolkit / Netty / Vert.x `java.util.logging` warnings — useful when a log isn't producing the events you expect and you want to see why.

## Sample output

This is the literal stdout of `gc-analyzer samples/gc-sample.log --no-color`, rendered in a monospace code block so the box-drawing characters line up:

```
=== JVM Summary ===
Collector       : G1GC
Log format      : unified (Java 9+)
Runtime         : 4.34s
First event     : 2026-04-10T22:13:56.837-07:00@0.004
Termination     : 2026-04-10T22:14:01.179-07:00@4.346
GC cycles       : 191 total
  Young                        81
  G1GCRemark                   37
  G1GCYoungInitialMark         37
  Mixed                        36

=== Heap After GC (MB, linear) ===
  224.00 ┤                     ╭╮
  206.15 ┤                ╭╮   ││╭╮╭╮                 ╭╮╭╮                 ╭╮╭╮╭╮
  188.30 ┤           ╭╮╭─╮││╭╮╭╯│││││╭╮╭╮╭╮╭╮     ╭╮╭╮││││╭╮╭╮╭╮╭╮╭╮     ╭╮││││││╭╮╭╮╭╮╭╮
  170.45 ┤          ╭╯││ ││╰╯││ ││││││││││││╰────╮│││││││││││││││││╰────╮│││││││││││││││╰
  152.61 ┤     ╭╮╭╮╭╯ ╰╯ ╰╯  ╰╯ ╰╯╰╯││││││╰╯     ╰╯╰╯╰╯╰╯││││││╰╯╰╯     ╰╯╰╯││╰╯││││││╰╯
  134.76 ┤   ╭─╯╰╯╰╯                ╰╯╰╯╰╯               ╰╯╰╯╰╯             ╰╯  ╰╯╰╯╰╯
  116.91 ┤  ╭╯
   99.06 ┤  │
   81.21 ┤ ╭╯
   63.36 ┤╭╯
   45.52 ┤│
   27.67 ┼╯

          t=0.30s ─────────────────────────────────────────────────────────────── t=4.33s   (Δ 4.03s)
(191 samples, 80 points plotted)

=== GC Pause Times (ms, log scale) ===
    4.47 ┼╮
    3.89 ┤│
    3.39 ┤│  ╭╮
    2.95 ┤│╭╮││
    2.57 ┤││╰╯│
    2.24 ┤││  │
    1.95 ┤││  ╰╮
    1.70 ┤││   ╰╮  ╭╮                  ╭╮
    1.48 ┤││    │╭╮│╰─╮  ╭╮ ╭╮  ╭╮╭╮   ││╭╮╭╮    ╭─╮ ╭╮       ╭╮  ╭╮  ╭╮    ╭─╮    ╭╮  ╭╮
    1.29 ┤││    ╰╯││  ╰─╮│╰╮││╭╮│╰╯╰──╮││││││╭╮╭╮│ ╰─╯╰╮╭╮╭╮╭╮││╭╮││╭╮││╭───╯ ╰─╮╭╮││╭╮││
    1.12 ┤╰╯      ╰╯    ╰╯ ╰╯╰╯││     ╰╯╰╯╰╯││╰╯╰╯     ╰╯││││╰╯╰╯││││╰╯││       ╰╯╰╯╰╯╰╯╰
    0.98 ┤                     ╰╯           ╰╯           ╰╯╰╯    ╰╯╰╯  ╰╯

          t=0.30s ─────────────────────────────────────────────────────────────── t=4.33s   (Δ 4.03s)
Total pauses: 191   Total pause time: 0.22s   % time paused: 5.00%   Max pause: 4.5 ms
(pauses ≤ 0.1 ms collapsed to Y-axis floor)

=== Diagnostics ===
  ✓ no pauses exceeded 500 ms (max: 4.5 ms)
  ⚠ application paused 5.00% of runtime (threshold 5%)
  ✓ post-GC heap is stable (142 MB → 169 MB)
  ✓ no System.gc() calls
  ✓ no Full GCs
```

On an actual terminal the diagnostics line markers (`✓`, `⚠`) render in green/yellow ANSI — the `--no-color` flag above just strips those so the README text stays clean.

Generate your own log to play with:

```bash
java -Xmx256m -Xms64m -XX:+UseG1GC \
     '-Xlog:gc*,safepoint:file=/tmp/gc.log:time,uptime,level,tags' \
     YourClass
./build/install/gc-analyzer/bin/gc-analyzer /tmp/gc.log
```

### With sample-point markers

Pass `--markers dot|cross|bullet` if you want to see exactly where each plotted bucket sits on the curve. Default is `off` because the unmarked smoothed line is generally more readable; markers are an opt-in for when you need to pick out specific data points.

```bash
./build/install/gc-analyzer/bin/gc-analyzer samples/gc-sample.log --markers cross
```

The three glyphs are calibrated lightest to heaviest:

- `dot` → `·` — subtle, the eye still reads the curve as continuous
- `cross` → `×` — slim, classic scientific-plot point marker
- `bullet` → `●` — prominent, pick out points at a glance but the line appears broken

## Project layout

- `src/main/java/com/gcanalyzer/Main.java` — picocli entry point
- `src/main/java/com/gcanalyzer/Analyzer.java` — orchestrates `GCToolKit.analyze()` and report ordering
- `src/main/java/com/gcanalyzer/aggregation/` — three `Aggregation` / `Aggregator` / `Summary` triples (heap occupancy, pause times, GC cycle counts), discovered by gctoolkit via `META-INF/services`
- `src/main/java/com/gcanalyzer/report/` — the four report printers plus `ChartUtil` (X-axis footer, log label rewrite, marker overlay) and `MarkerStyle`
- `src/main/resources/META-INF/services/com.microsoft.gctoolkit.aggregator.Aggregation` — service-loader registration for the three summary classes
- `samples/` — sample GC log used by the smoke test

## License

MIT
