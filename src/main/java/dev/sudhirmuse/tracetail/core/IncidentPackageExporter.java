/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class IncidentPackageExporter {
    public void export(Path destination, List<DiagnosticEvent> events) throws IOException {
        LogRedactor redactor = new LogRedactor(); List<Map<String,Object>> rows = events.stream().limit(100_000).map(item -> {
            Map<String,Object> row = new LinkedHashMap<>(); row.put("source", item.source().getFileName().toString()); row.put("timestamp", item.timestamp().toString());
            row.put("level", item.event().level()); row.put("threadId", redactor.redact(item.event().threadId())); row.put("traceId", redactor.redact(item.event().traceId())); row.put("content", redactor.redact(item.event().content())); return row;
        }).toList();
        long errors = events.stream().filter(item -> item.event().level().atLeast(LogLevel.ERROR)).count(); String summary = "# TraceTail Incident Package\n\nGenerated: " + Instant.now() + "\n\nEvents: " + rows.size() + "\n\nErrors: " + errors + "\n\nAll exported event content was passed through TraceTail redaction.\n";
        byte[] json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsBytes(rows);
        Path parent = destination.toAbsolutePath().getParent(); if (parent != null) Files.createDirectories(parent);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(destination))) { entry(zip,"README.md",summary.getBytes(StandardCharsets.UTF_8)); entry(zip,"events.json",json); }
    }
    private static void entry(ZipOutputStream zip, String name, byte[] data) throws IOException { zip.putNextEntry(new ZipEntry(name)); zip.write(data); zip.closeEntry(); }
}
