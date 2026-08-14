/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LogRunComparatorTest {
    private LogEvent event(long sequence, String text) { return new LogEvent(sequence, sequence, Instant.EPOCH, LogLevel.INFO, "", "", text, text); }
    @Test void comparesNormalizedPatternFrequency() {
        var deltas = new LogRunComparator().compare(List.of(event(1, "order 123 failed")), List.of(event(1, "order 456 failed"), event(2, "order 789 failed")));
        assertEquals(1, deltas.size()); assertEquals(1, deltas.getFirst().difference()); assertEquals("order <n> failed", deltas.getFirst().pattern());
    }
}
