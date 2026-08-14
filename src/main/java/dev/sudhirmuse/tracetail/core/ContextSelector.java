/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ContextSelector {
    private ContextSelector() { }

    public enum Mode {
        MATCH_ONLY("Matches only"), LINES_5("±5 events"), LINES_20("±20 events"),
        SAME_THREAD("Same thread"), SAME_TRACE("Same trace ID");
        private final String label;
        Mode(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public static List<LogEvent> select(List<LogEvent> events, SearchPattern search, Mode mode) {
        Set<Integer> selected = new LinkedHashSet<>();
        Set<String> threads = new LinkedHashSet<>();
        Set<String> traces = new LinkedHashSet<>();
        int radius = mode == Mode.LINES_5 ? 5 : mode == Mode.LINES_20 ? 20 : 0;
        for (int index = 0; index < events.size(); index++) {
            LogEvent event = events.get(index);
            if (!search.matches(event.content())) continue;
            if (mode == Mode.SAME_THREAD && !event.threadId().isBlank()) threads.add(event.threadId());
            else if (mode == Mode.SAME_TRACE && !event.traceId().isBlank()) traces.add(event.traceId());
            else for (int candidate = Math.max(0, index - radius); candidate <= Math.min(events.size() - 1, index + radius); candidate++) selected.add(candidate);
        }
        if (mode == Mode.SAME_THREAD) {
            for (int index = 0; index < events.size(); index++) if (threads.contains(events.get(index).threadId())) selected.add(index);
        } else if (mode == Mode.SAME_TRACE) {
            for (int index = 0; index < events.size(); index++) if (traces.contains(events.get(index).traceId())) selected.add(index);
        }
        return selected.stream().sorted().map(events::get).toList();
    }
}
