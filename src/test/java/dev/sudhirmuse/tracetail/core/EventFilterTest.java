/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventFilterTest {
    private final LogEvent event = new LogEvent(1, Instant.EPOCH, LogLevel.ERROR, "trace-42", "Payment failed", "Payment failed for order 123");

    @Test void filtersByLevelTextAndTrace() {
        assertTrue(new EventFilter("payment", false, LogLevel.WARN, "trace-42").predicate().test(event));
        assertFalse(new EventFilter("checkout", false, LogLevel.WARN, "trace-42").predicate().test(event));
        assertFalse(new EventFilter("payment", false, LogLevel.FATAL, "trace-42").predicate().test(event));
    }

    @Test void validatesAndAppliesRegularExpressions() {
        assertTrue(EventFilter.validRegex("order\\s+\\d+"));
        assertFalse(EventFilter.validRegex("[broken"));
        assertTrue(new EventFilter("order\\s+\\d+", true, LogLevel.TRACE, "").predicate().test(event));
    }
}
