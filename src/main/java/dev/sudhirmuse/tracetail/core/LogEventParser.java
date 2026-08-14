/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LogEventParser {
    private static final Pattern LEVEL = Pattern.compile("(?i)(?:^|[\\s\\[\"',])(?:level[\"']?\\s*[:=]\\s*[\"']?)?(TRACE|DEBUG|INFO|WARN(?:ING)?|ERROR|FATAL)(?:[\"'\\]\\s,:-]|$)");
    private static final List<Pattern> TRACE_PATTERNS = List.of(
        Pattern.compile("(?i)(?:trace[_-]?id|correlation[_-]?id|request[_-]?id)[\"']?\\s*[:=]\\s*[\"']?([A-Za-z0-9._-]{4,128})"),
        Pattern.compile("\\b([0-9a-fA-F]{32})\\b"),
        Pattern.compile("\\b([0-9a-fA-F]{16})\\b")
    );
    private static final Pattern THREAD = Pattern.compile("\\[([^]\\r\\n]{1,160})]");

    private final Clock clock;
    private final LogRedactor redactor;
    private final ObjectMapper mapper;
    private final StringBuilder pending = new StringBuilder();
    private long sequence;
    private long pendingLineNumber;
    private long inferredLineNumber;

    public LogEventParser() {
        this(Clock.systemUTC(), new LogRedactor(), new ObjectMapper());
    }

    LogEventParser(Clock clock, LogRedactor redactor, ObjectMapper mapper) {
        this.clock = clock;
        this.redactor = redactor;
        this.mapper = mapper;
    }

    public List<LogEvent> accept(String line) {
        return accept(line, ++inferredLineNumber);
    }

    public List<LogEvent> accept(String line, long lineNumber) {
        List<LogEvent> emitted = new ArrayList<>(1);
        String normalized = line == null ? "" : line.replace("\uFEFF", "").replace("\uFFFD", "");
        if (startsNewEvent(normalized) && !pending.isEmpty()) emitted.add(toEvent(pending.toString()));
        if (startsNewEvent(normalized)) {
            pending.setLength(0);
            pendingLineNumber = lineNumber;
        }
        if (!pending.isEmpty()) pending.append('\n');
        pending.append(normalized);
        return emitted;
    }

    public List<LogEvent> finish() {
        if (pending.isEmpty()) return List.of();
        LogEvent event = toEvent(pending.toString());
        pending.setLength(0);
        return List.of(event);
    }

    public void reset() { pending.setLength(0); pendingLineNumber = 0; inferredLineNumber = 0; }

    private boolean startsNewEvent(String line) {
        if (pending.isEmpty()) return true;
        if (line.isBlank()) return false;
        if (Character.isWhitespace(line.charAt(0))) return false;
        String trimmed = line.stripLeading();
        return !(trimmed.startsWith("Caused by:") || trimmed.startsWith("Suppressed:")
            || trimmed.matches("^\\.\\.\\. \\d+ more.*") || trimmed.startsWith("at "));
    }

    private LogEvent toEvent(String raw) {
        String content = redactor.redact(raw).stripTrailing();
        LogLevel level = extractLevel(content);
        String traceId = extractTraceId(content).orElse("");
        String threadId = extractThreadId(content).orElse("");
        String summary = content.lines().findFirst().orElse("").strip();
        if (summary.length() > 240) summary = summary.substring(0, 237) + "...";
        return new LogEvent(++sequence, pendingLineNumber, clock.instant(), level, threadId, traceId, summary, content);
    }

    private LogLevel extractLevel(String content) {
        Matcher matcher = LEVEL.matcher(content.lines().findFirst().orElse(""));
        if (!matcher.find()) return LogLevel.UNKNOWN;
        String value = matcher.group(1).toUpperCase(Locale.ROOT);
        return value.equals("WARNING") ? LogLevel.WARN : LogLevel.valueOf(value);
    }

    private Optional<String> extractTraceId(String content) {
        String firstLine = content.lines().findFirst().orElse("");
        for (Pattern pattern : TRACE_PATTERNS) {
            Matcher matcher = pattern.matcher(firstLine);
            if (matcher.find()) return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }

    private Optional<String> extractThreadId(String content) {
        Matcher matcher = THREAD.matcher(content.lines().findFirst().orElse(""));
        while (matcher.find()) {
            String candidate = matcher.group(1).strip();
            if (!candidate.toLowerCase(Locale.ROOT).contains("traceid=")) return Optional.of(candidate);
        }
        return Optional.empty();
    }
}
