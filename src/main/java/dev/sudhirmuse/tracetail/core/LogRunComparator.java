/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class LogRunComparator {
    private static final Pattern UUID = Pattern.compile("\\b[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}\\b");
    private static final Pattern NUMBER = Pattern.compile("\\b\\d+(?:\\.\\d+)?\\b");
    private static final Pattern HEX = Pattern.compile("\\b[0-9a-fA-F]{12,}\\b");

    public List<Delta> compare(List<LogEvent> left, List<LogEvent> right) {
        Map<String, Long> a = counts(left); Map<String, Long> b = counts(right);
        java.util.Set<String> patterns = new java.util.LinkedHashSet<>(a.keySet()); patterns.addAll(b.keySet());
        return patterns.stream().map(pattern -> new Delta(pattern, a.getOrDefault(pattern, 0L), b.getOrDefault(pattern, 0L)))
            .filter(delta -> delta.leftCount() != delta.rightCount())
            .sorted(java.util.Comparator.comparingLong((Delta value) -> Math.abs(value.difference())).reversed()).toList();
    }

    private Map<String, Long> counts(List<LogEvent> events) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (LogEvent event : events) counts.merge(normalize(event.summary()), 1L, Long::sum);
        return counts;
    }

    private String normalize(String value) { return NUMBER.matcher(HEX.matcher(UUID.matcher(value).replaceAll("<uuid>")).replaceAll("<hex>")).replaceAll("<n>"); }
    public record Delta(String pattern, long leftCount, long rightCount) { public long difference() { return rightCount - leftCount; } }
}
