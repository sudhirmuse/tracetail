/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SecurityAndIncidentTest {
    @TempDir Path directory;
    @Test void redactsJwtEmailCardAndSecrets(){String input="email=sudhir@example.com card=4111 1111 1111 1111 token=abc eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.signature";String value=new LogRedactor().redact(input);assertFalse(value.contains("sudhir@example.com"));assertFalse(value.contains("4111"));assertFalse(value.contains("abc"));assertTrue(value.contains("<jwt-redacted>"));}
    @Test void incidentPackageContainsOnlyRedactedEvents()throws Exception{String content="password=hunter2 email=sudhir@example.com";LogEvent event=new LogEvent(1,1,Instant.now(),LogLevel.ERROR,"","",content,content);Path zip=directory.resolve("incident.zip");new IncidentPackageExporter().export(zip,List.of(new DiagnosticEvent(Path.of("app.log"),Instant.now(),event)));try(ZipFile file=new ZipFile(zip.toFile())){String json=new String(file.getInputStream(file.getEntry("events.json")).readAllBytes());assertFalse(json.contains("hunter2"));assertFalse(json.contains("sudhir@example.com"));}}
}
