# Architecture

TraceTail separates file I/O and parsing from JavaFX presentation so the behavior that handles production logs remains headlessly testable.

## Data flow

1. `FileTailer` reads appended UTF-8 bytes in bounded chunks and emits complete lines.
2. `LogEventParser` groups lines into events, redacts sensitive text, and extracts level and trace metadata.
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
