/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ContextSelectorTest {
    private LogEvent event(long line, String thread, String trace, String text) {
        return new LogEvent(line, line, Instant.EPOCH, LogLevel.INFO, thread, trace, text, text);
    }

    @Test void includesBoundedNeighbouringEventsWithoutDuplicates() {
        List<LogEvent> events = java.util.stream.LongStream.range(0, 30)
            .mapToObj(index -> event(index, "t", "", index == 10 || index == 12 ? "ERROR" : "INFO")).toList();
        assertEquals(13, ContextSelector.select(events, SearchPattern.compile("ERROR", false), ContextSelector.Mode.LINES_5).size());
    }

    @Test void selectsEveryEventFromMatchingThread() {
        List<LogEvent> events = List.of(event(1, "worker-a", "x", "ERROR"), event(2, "worker-b", "", "INFO"), event(3, "worker-a", "", "INFO"));
        assertEquals(List.of(1L, 3L), ContextSelector.select(events, SearchPattern.compile("ERROR", false), ContextSelector.Mode.SAME_THREAD)
            .stream().map(LogEvent::lineNumber).toList());
    }
}
