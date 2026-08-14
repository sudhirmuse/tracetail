# Advanced feature matrix

TraceTail 0.2 provides an original implementation of the practical workflows associated with advanced desktop tail viewers. It does not include or derive from BareTail source code, branding, or visual assets.

| Capability | TraceTail 0.2 implementation |
|---|---|
| Real-time follow and rotation recovery | Explicit Follow Tail control; incremental bounded reads; truncation, replacement, and rename recovery |
| Multiple files | Independent closeable tabs with unread/error/paused indicators |
| Very large files | 64-bit sparse line index, fixed-memory 5,000-line pages, eight-page LRU cache, direct percentage fallback while indexing |
| Search | Incremental literal or Java-regex search with syntax feedback |
| Whole-file search | Asynchronous disk-backed page scan with progress, cancellation, and bounded displayed results |
| Match navigation | Next/previous with wraparound and keyboard shortcuts |
| Live filtering | Highlight, include, or exclude modes |
| Search results | Sortable received-time, line, level, trace, capture-group, and message columns |
| Saved patterns | Persistent named searches |
| Export | Visible results to text, CSV, JSON, or clipboard |
| Highlighting | Ordered persistent regex/literal rules with foreground/background colours |
| Encodings/formats | UTF-8, Windows-1252, ISO-8859-1, UTF-16LE/BE; CRLF/LF; safe NUL/control display |
| Display | Line wrapping, font size, TAB expansion, details split view, tab placement |
| Preferences | Local JSON persistence plus import/export; no registry dependency |
| Startup | Multiple paths, recent-file reopening, window position/state, always-on-top |
| Java diagnostics | Multiline stack grouping, log levels, trace IDs, JSON pretty printing, secret redaction |

The self-contained Windows archive includes `TraceTail.exe` and a private Java runtime. This is intentionally larger than a native Win32-only executable, but it requires no separately installed Java runtime.
