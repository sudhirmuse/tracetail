/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LogTimestampParserTest {
    private final LogTimestampParser parser = new LogTimestampParser();
    @Test void parsesIsoAndConventionalJavaTimestamps() {
        assertEquals("2026-08-14T10:00:07Z", parser.parse("{\"timestamp\":\"2026-08-14T10:00:07Z\"}").orElseThrow().toString());
        assertTrue(parser.parse("2026-08-14 10:00:00.001 INFO ready").isPresent());
    }
    @Test void rejectsLinesWithoutTimestamp() { assertTrue(parser.parse("ERROR without timestamp").isEmpty()); }
}
