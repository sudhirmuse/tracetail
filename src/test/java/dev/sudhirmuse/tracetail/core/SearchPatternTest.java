/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SearchPatternTest {
    @Test void findsLiteralTextIgnoringCase() {
        SearchPattern pattern = SearchPattern.compile("error", false);
        assertTrue(pattern.matches("ERROR while starting"));
        assertEquals(1, pattern.ranges("an Error occurred").size());
    }

    @Test void supportsRegularExpressionsAndMultipleRanges() {
        SearchPattern pattern = SearchPattern.compile("trace-\\d+", true);
        assertEquals(2, pattern.ranges("trace-12 then TRACE-99").size());
    }

    @Test void reportsInvalidRegularExpression() {
        SearchPattern pattern = SearchPattern.compile("[", true);
        assertFalse(pattern.valid());
        assertFalse(pattern.matches("anything"));
    }

    @Test void exposesCapturingGroups() {
        SearchPattern pattern = SearchPattern.compile("order=(\\d+).*status=(\\w+)", true);
        assertEquals(java.util.List.of("42", "FAILED"), pattern.groups("order=42 status=FAILED"));
    }
}
