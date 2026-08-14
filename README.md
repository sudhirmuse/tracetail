# TraceTail

TraceTail is an advanced desktop log viewer for Java and Spring applications. It follows multiple files in real time, groups multiline stack traces into single events, and makes high-volume logs searchable without sending data anywhere. Version 0.2 adds an original BareTailPro-style workflow while retaining TraceTail's Java diagnostics focus.

## Features

- Open several log files in independent tabs.
- Follow appended data and recover from file truncation or rotation.
- Group Java stack traces, `Caused by`, and `Suppressed` blocks with their originating event.
- Detect `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`, and `FATAL` levels.
- Extract common trace/correlation IDs.
- Extract thread IDs in both Fast View and Analyze mode, display them in a dedicated column, and filter by partial thread name.
- Incrementally search using plain text or regular expressions, with immediate syntax feedback.
- Highlight matches without hiding context, or include/exclude matching events from the live tail.
- Navigate matches with `F3` / `Shift+F3`; display regex capture groups in a sortable column.
- Save and reuse named search patterns.
- Export visible results to CSV, JSON, or text, or copy them to the clipboard.
- Show received time, source line, level, trace ID, capture groups, and message in sortable columns.
- Pause display updates without losing newly tailed events.
- Find text without hiding surrounding log lines, highlight every visible match, and navigate with `F3` / `Shift+F3`.
- Use case-insensitive literal or regular-expression search with **Highlight**, **Include**, and **Exclude** modes.
- Choose **Show matches only** to hide non-matching lines, or **Hide matches** to suppress matching lines.
- Configure persistent highlight rules with custom foreground/background colours.
- Highlight complete matching rows and open the rule manager directly beside **Follow Tail**.
- Apply shared highlight rules immediately to every open tab and automatically to files opened or dragged in later.
- Browse a workspace and persistent Scratch folder in the left explorer, open individual files or all supported files in a folder, and save exactly the current search matches to a new Scratch log.
- Capture search context to Scratch as matches only, ±5/±20 events, same thread, or same trace ID.
- Merge events from every open file into a timestamp-sorted, source-coloured timeline.
- Visualize severity over time in a clickable histogram and open any time bucket as a timeline.
- Summarize complete thread/trace journeys across files with duration and severity counts.
- Extract and exactly filter JSON and `logfmt` fields with `field=value`.
- Bookmark events with investigation notes and save/load named investigation sessions.
- Compare normalized message-pattern frequencies between two logs.
- Open `.gz` and `.zip` log archives asynchronously.
- Configure throttled live alert rules with desktop notifications and optional automatic Scratch capture.
- Open remote logs from HTTP/HTTPS, SSH/SFTP, Docker, Kubernetes, AWS CloudWatch, Azure Monitor, and GCP Logging. SSH supports host, port, username, private-key selection, remote-file discovery, and bounded live tailing. Docker discovers containers on local or remote engines. Kubernetes discovers contexts, namespaces, pods, and containers and supports live or previous-container logs. Live sources stream into a local spool so all normal search, highlight, filtering, and follow-tail features continue to work.
- Choose a persistent Scratch location from **Preferences → Scratch Folder**. Existing tabs, alerts, and new captures immediately use the selected folder.
- Open an on-demand Structured View for JSON, XML, YAML, properties/INI, CSV/TSV, SQL, Markdown, and plain text. TraceTail detects known content, provides raw and formatted tabs, exposes JSON/XML/YAML trees, and saves formatting only as a new copy. Structured parsing is capped at 32 MiB so Fast View remains safe for multi-gigabyte logs.
- Compare arbitrary text or structured files side by side or as a unified diff, optionally ignoring whitespace. JSON, XML, and YAML comparisons can be semantic, avoiding false changes from property or XML attribute ordering.
- Use **Investigate → Developer Diagnostics** for grouped stack-trace root causes and first application frames, cross-service trace journeys, latency percentiles, logging-health findings, thread-dump and GC analysis, effective configuration comparison, and sanitized incident ZIP export.
- Inspect and compare JAR/WAR/EAR artifacts, browse class/version/manifest metadata, and decompile a selected class on demand with CFR.
- Decode JWT/Base64/URL/JSON payloads and reconstruct sanitized curl commands without exposing captured credentials.
- Create declarative YAML/JSON parser profiles for proprietary formats. Profiles define timestamp, level, thread, trace, and custom-column patterns without loading executable plugins.
- Apply export-time sensitive-data protection in both Analyze and Fast View modes, covering credentials, authorization/cookies, JWTs, email addresses, payment-card-like values, and user-home paths.
- Read a fixed-memory history window from any percentage of a very large file, then resume live tailing.
- Navigate by 64-bit line number using a sparse background index and bounded LRU page cache, including files larger than 2 GB.
- Open in **Fast View** by default: load only a 128 KiB tail window with no whole-file scan; enable **Analyze** only when structured Java/Spring parsing is needed.
- Search the entire indexed file asynchronously with progress and cancellation instead of limiting search to the visible tail.
- Select UTF-8, Windows-1252, ISO-8859-1, UTF-16LE, or UTF-16BE input and configure TAB expansion.
- Configure wrapping, font size, tab placement, always-on-top, recent-file reopening, and portable JSON preferences.
- Switch between persistent Dark, Light, and Windows System themes.
- Switch themes instantly from **View → Theme** as well as Preferences.
- Mark background tabs when new data arrives and mark tail errors visually.
- Pretty-print JSON log messages in the details panel.
- Redact passwords, tokens, authorization headers, cookies, API keys, and user-home paths.
- Bound each tab to 20,000 events to prevent unbounded memory growth.
- Keep all processing local; TraceTail has no telemetry or network client.

## Requirements

The portable distribution requires Java 21. Platform runtime archives include a private Java runtime and do not require a separate Java installation.

## Install

Download an archive from [GitHub Releases](https://github.com/sudhirmuse/tracetail/releases):

- `TraceTail-<version>-windows-x64.zip`: self-contained Windows application; extract and run `TraceTail.exe`.
- `tracetail-<version>.zip`: portable scripts and libraries for systems with Java 21.

TraceTail currently publishes a self-contained Windows x64 build. The portable archive and source build support other desktop platforms where JavaFX 21 is available.

## Build

```bash
./gradlew build
./gradlew installDist
```

Run locally:

```bash
./gradlew run
```

On Windows:

```powershell
.\gradlew.bat run
```

## Keyboard shortcuts

| Shortcut | Action |
|---|---|
| `Ctrl+O` | Open log file |
| `Ctrl+F` | Focus filter |
| `Ctrl+P` | Pause or resume active tab |
| `Ctrl+T` | Toggle Follow Tail |
| `Ctrl+L` | Clear active tab |
| `Ctrl+W` | Close active tab |
| `F3` | Next search match |
| `Shift+F3` | Previous search match |
| `Ctrl+E` | Export visible results |
| `Ctrl+Shift+C` | Copy visible results |
| `Ctrl+Shift+S` | Save current search |

## Command line

```text
tracetail [options] [log-file ...]
  --window-position, -wp LEFT TOP WIDTH HEIGHT
  --window-state, -ws normal|minimized|maximized
  --no-reopen
```

Multiple files open in tabs. Display and search preferences are stored in `%USERPROFILE%\.tracetail\preferences.json` and may also be imported or exported through the application.

## Parsing model

A non-indented line starts a new event. Indented lines and lines beginning with `Caused by:`, `Suppressed:`, or `... N more` are attached to the preceding event. This matches conventional Java text logs while still supporting one-event-per-line JSON logs.

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).
