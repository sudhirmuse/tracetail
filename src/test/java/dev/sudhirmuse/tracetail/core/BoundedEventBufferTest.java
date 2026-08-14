/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BoundedEventBufferTest {
    @Test void evictsOldestEventsAtCapacity() {
        BoundedEventBuffer buffer = new BoundedEventBuffer(2);
        buffer.addAll(List.of(event(1), event(2), event(3)));
        assertEquals(List.of(2L, 3L), buffer.snapshot().stream().map(LogEvent::sequence).toList());
    }

    private LogEvent event(long sequence) {
        return new LogEvent(sequence, Instant.EPOCH, LogLevel.INFO, "", "event", "event");
    }
}
