/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import java.util.ArrayList;
import java.util.List;

public final class BoundedEventBuffer {
    private final int capacity;
    private final List<LogEvent> events = new ArrayList<>();

    public BoundedEventBuffer(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
    }

    public synchronized void addAll(List<LogEvent> additions) {
        if (additions.isEmpty()) return;
        events.addAll(additions);
        int overflow = events.size() - capacity;
        if (overflow > 0) events.subList(0, overflow).clear();
    }

    public synchronized List<LogEvent> snapshot() { return List.copyOf(events); }
    public synchronized void clear() { events.clear(); }
    public synchronized int size() { return events.size(); }
}
