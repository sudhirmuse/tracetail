/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogEventParserTest {
    private LogEventParser parser() {
        return new LogEventParser(Clock.fixed(Instant.parse("2026-08-14T10:00:00Z"), ZoneOffset.UTC), new LogRedactor(), new ObjectMapper());
    }

    @Test
    void groupsMultilineJavaStackTraceAndExtractsMetadata() {
        LogEventParser parser = parser();
        assertTrue(parser.accept("2026-08-14 10:00:00 ERROR [http-worker-7] [traceId=abc-123] Checkout failed").isEmpty());
        assertTrue(parser.accept("    at com.acme.Checkout.pay(Checkout.java:42)").isEmpty());
        assertTrue(parser.accept("Caused by: java.io.IOException: timeout").isEmpty());
        var emitted = parser.accept("2026-08-14 10:00:01 INFO Recovered");
        assertEquals(1, emitted.size());
        assertEquals(LogLevel.ERROR, emitted.getFirst().level());
        assertEquals(1, emitted.getFirst().lineNumber());
        assertEquals("abc-123", emitted.getFirst().traceId());
        assertEquals("http-worker-7", emitted.getFirst().threadId());
        assertTrue(emitted.getFirst().content().contains("Caused by:"));
        assertEquals(LogLevel.INFO, parser.finish().getFirst().level());
    }

    @Test
    void parsesJsonLevelAndRedactsSecretsAndHomePaths() {
        LogEventParser parser = parser();
        parser.accept("{\"level\":\"WARN\",\"token\":\"abc\",\"path\":\"C:\\\\Users\\\\sudhir\\\\app\"}");
        LogEvent event = parser.finish().getFirst();
        assertEquals(LogLevel.WARN, event.level());
        assertFalse(event.content().contains("abc"));
        assertFalse(event.content().contains("sudhir"));
        assertTrue(event.content().contains("<redacted>"));
    }

    @Test
    void resetDiscardsPendingContent() {
        LogEventParser parser = parser();
        parser.accept("INFO this event should be discarded");
        parser.reset();
        assertTrue(parser.finish().isEmpty());
    }
}
