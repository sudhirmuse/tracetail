# Changelog

## 0.2.0 - 2026-08-14

- Added incremental literal/regex highlighting with next/previous navigation.
- Added include and exclude live-tail modes, sortable line/capture-group columns, saved searches, and CSV/JSON/text export.
- Added configurable highlight rules, recent files, portable preferences, wrapping/font/tab controls, and always-on-top.
- Added bounded random-position history windows for very large files and selectable character encodings.
- Added file-status tab indicators, command-line window placement/state, and expanded automated coverage.
- Made file opening non-blocking and reduced UI parsing batches for much faster perceived startup on large logs.
- Added a sparse 64-bit line index, fixed-memory paged history, bounded LRU caching, and cancellable whole-file search for multi-gigabyte logs.
- Added contextual Scratch capture, merged timelines, clickable severity histograms, thread/trace journeys, structured-field filtering, bookmarks, sessions, log comparison, compressed logs, and live alerts.
- Added remote HTTP/HTTPS, SSH, Docker, Kubernetes, CloudWatch, Azure Monitor, and GCP Logging sources, plus a configurable persistent Scratch folder.
- Added opt-in Structured View with format detection, JSON/XML/YAML trees, safe formatted-copy export, and side-by-side/unified semantic comparison.
- Added Developer Diagnostics: exception intelligence, correlation, performance and log-quality reports, sanitized incident packages, configuration/thread-dump/GC analysis, artifact inspection and CFR decompilation, payload tools, parser profiles, and stronger export redaction.

## 0.1.0 - 2026-08-14

- Multi-file JavaFX log viewer with live tail and pause/resume.
- Multiline Java stack-trace grouping and level/trace extraction.
- Text, regex, level, and correlation-ID filters.
- JSON detail formatting and local secret/path redaction.
- Rotation/truncation recovery and bounded 20,000-event storage per tab.
- Drag-and-drop, keyboard shortcuts, and dark desktop theme.
