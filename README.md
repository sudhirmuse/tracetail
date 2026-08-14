# TraceTail

TraceTail is a focused desktop log viewer for Java and Spring applications. It follows multiple files in real time, groups multiline stack traces into single events, and makes high-volume logs searchable without sending data anywhere.

## Features

- Open several log files in independent tabs.
- Follow appended data and recover from file truncation or rotation.
- Group Java stack traces, `Caused by`, and `Suppressed` blocks with their originating event.
- Detect `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`, and `FATAL` levels.
- Extract common trace/correlation IDs.
- Filter by level, trace ID, plain text, or regular expression.
- Pause display updates without losing newly tailed events.
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
| `Ctrl+L` | Clear active tab |
| `Ctrl+W` | Close active tab |

## Parsing model

A non-indented line starts a new event. Indented lines and lines beginning with `Caused by:`, `Suppressed:`, or `... N more` are attached to the preceding event. This matches conventional Java text logs while still supporting one-event-per-line JSON logs.

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).
