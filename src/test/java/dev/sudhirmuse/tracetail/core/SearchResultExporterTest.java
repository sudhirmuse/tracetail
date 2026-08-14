/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchResultExporterTest {
    private final LogEvent event = new LogEvent(1, 42, Instant.EPOCH, LogLevel.ERROR, "worker-1", "trace-1", "failed", "order=123, failed");

    @Test void exportsCsvWithLineAndCaptureGroup() throws Exception {
        String csv = SearchResultExporter.export(List.of(event), SearchPattern.compile("order=(\\d+)", true), SearchResultExporter.Format.CSV);
        assertTrue(csv.contains("42"));
        assertTrue(csv.contains("123"));
    }

    @Test void exportsJson() throws Exception {
        String json = SearchResultExporter.export(List.of(event), SearchPattern.compile("failed", false), SearchResultExporter.Format.JSON);
        assertTrue(json.contains("\"lineNumber\" : 42"));
    }
}
