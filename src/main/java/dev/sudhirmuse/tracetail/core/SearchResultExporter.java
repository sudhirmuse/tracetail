/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SearchResultExporter {
    private static final LogRedactor REDACTOR = new LogRedactor();
    private SearchResultExporter() { }

    public enum Format { TEXT, CSV, JSON }

    public static String export(List<LogEvent> events, SearchPattern search, Format format) throws IOException {
        return switch (format) {
            case TEXT -> text(events, search);
            case CSV -> csv(events, search);
            case JSON -> json(events, search);
        };
    }

    private static String text(List<LogEvent> events, SearchPattern search) {
        StringBuilder result = new StringBuilder();
        for (LogEvent event : events) {
            result.append(event.receivedAt()).append('\t').append(event.lineNumber()).append('\t')
                .append(event.level()).append('\t').append(event.threadId()).append('\t').append(REDACTOR.redact(event.content()).replace("\n", "\\n"));
            for (String group : search.groups(event.content())) result.append('\t').append(REDACTOR.redact(group));
            result.append(System.lineSeparator());
        }
        return result.toString();
    }

    private static String csv(List<LogEvent> events, SearchPattern search) {
        StringBuilder result = new StringBuilder("receivedAt,lineNumber,level,threadId,traceId,text,captureGroups\r\n");
        for (LogEvent event : events) {
            result.append(csv(event.receivedAt().toString())).append(',').append(event.lineNumber()).append(',')
                .append(event.level()).append(',').append(csv(event.threadId())).append(',').append(csv(event.traceId())).append(',')
                .append(csv(REDACTOR.redact(event.content()))).append(',').append(csv(REDACTOR.redact(String.join(" | ", search.groups(event.content()))))).append("\r\n");
        }
        return result.toString();
    }

    private static String json(List<LogEvent> events, SearchPattern search) throws IOException {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LogEvent event : events) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("receivedAt", event.receivedAt().toString());
            row.put("lineNumber", event.lineNumber());
            row.put("level", event.level());
            row.put("threadId", event.threadId());
            row.put("traceId", event.traceId());
            row.put("text", REDACTOR.redact(event.content()));
            row.put("captureGroups", search.groups(event.content()).stream().map(REDACTOR::redact).toList());
            rows.add(row);
        }
        return new ObjectMapper().findAndRegisterModules().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(rows);
    }

    private static String csv(String value) { return '"' + value.replace("\"", "\"\"") + '"'; }
}
