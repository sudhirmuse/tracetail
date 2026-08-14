# Architecture

## Version 0.2 additions

- `SearchPattern` centralises safe literal/regex compilation, highlighting ranges, and capture groups.
- `SearchResultExporter` produces deterministic text, CSV, and JSON result representations.
- `FileWindowReader` provides fixed-memory random-position history access for very large files.
- `AppPreferences` and `PreferenceStore` persist and port display, highlight, search, and recent-file settings as JSON.
- `LaunchOptions` validates desktop window and multi-file command-line arguments before UI startup.

## Large-file engine

TraceTail does not load an entire file into Java objects. Each open tab owns four independent bounded workers/models:

1. `FileTailer` follows recent writes in 256 KiB batches and recovers from truncation or replacement.
2. `SparseLineIndex` starts only on demand, scans with a reusable 4 MiB direct buffer, and records one primitive 64-bit byte offset per 1,024 lines.
3. `PagedLineReader` resolves a requested line from the nearest checkpoint and retains at most eight 5,000-line pages in an access-ordered LRU cache.
4. `LargeFileSearcher` scans disk-backed pages on a worker thread with cooperative cancellation and a bounded displayed-result set.

The default Fast View reads only the final 128 KiB and creates lightweight raw-line events. Structured stack grouping, trace extraction, JSON handling, and redaction are opt-in through Analyze. The JavaFX thread receives only bounded event batches or completed pages. File size therefore does not determine heap usage, and opening a file never starts a whole-file scan.

TraceTail separates file I/O and parsing from JavaFX presentation so the behavior that handles production logs remains headlessly testable.

## Data flow

1. `FileTailer` reads appended UTF-8 bytes in bounded chunks and emits complete lines.
2. `LogEventParser` groups lines into events, redacts sensitive text, and extracts level and trace metadata.

Developer Diagnostics operates only on bounded event snapshots or explicitly selected files. Text analyzers cap inputs at 64 MiB, Structured View caps parsing at 32 MiB, artifact inspection caps archive entries, and source decompilation runs only for a selected class. Declarative parser profiles contain regular expressions and column metadata rather than executable code. Incident exports pass all fields through `LogRedactor`, including events created by raw Fast View.
3. `BoundedEventBuffer` retains at most 20,000 events per file.
4. JavaFX presents a filtered view of the retained events and a complete event detail panel.

File callbacks run on a daemon executor. UI mutations are marshalled onto the JavaFX application thread. Pausing affects only presentation; ingestion and bounded retention continue.

## Failure boundaries

- A truncated or replaced file resets the byte offset and partial-line state.
- Missing files remain retryable for rotation scenarios.
- Invalid regular expressions do not replace the last valid filter.
- Invalid JSON remains readable as original text.
- Tail errors are shown in the status bar and do not terminate the application.

## Privacy

TraceTail has no HTTP client, analytics, crash reporter, or telemetry. Redaction happens before events enter the retained buffer.
