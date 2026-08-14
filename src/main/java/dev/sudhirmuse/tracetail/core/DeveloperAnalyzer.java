/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DeveloperAnalyzer {
    private static final Pattern EXCEPTION = Pattern.compile("(?m)(?:Caused by:\\s*)?([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*(?:Exception|Error))(?::\\s*([^\\r\\n]*))?");
    private static final Pattern FRAME = Pattern.compile("(?m)^\\s*at\\s+([\\w.$]+)\\(([^)]+)\\)");
    private static final Pattern DURATION = Pattern.compile("(?i)\\b(?:duration|elapsed|latency|took|completed in)\\s*[=:]?\\s*(\\d+(?:\\.\\d+)?)\\s*(ms|s|us|µs)\\b");
    private static final Pattern STATUS = Pattern.compile("(?m)java.lang.Thread.State:\\s*([A-Z_]+)");
    private static final Pattern GC_PAUSE = Pattern.compile("(?i)(?:pause|gc\\([^)]*\\)).*?(\\d+(?:\\.\\d+)?)ms");

    public String stackTraces(List<DiagnosticEvent> events) {
        Map<String, StackGroup> groups = new LinkedHashMap<>();
        for (DiagnosticEvent item : events) { Matcher matcher = EXCEPTION.matcher(item.event().content()); if (!matcher.find()) continue;
            String root = matcher.group(1); String message = matcher.group(2) == null ? "" : matcher.group(2); while (matcher.find()) { root = matcher.group(1); message = matcher.group(2) == null ? "" : matcher.group(2); }
            Matcher frame = FRAME.matcher(item.event().content()); String applicationFrame = "";
            while (frame.find()) { String name = frame.group(1); if (!name.startsWith("java.") && !name.startsWith("jdk.") && !name.startsWith("org.springframework.") && !name.startsWith("reactor.")) { applicationFrame = name + "(" + frame.group(2) + ")"; break; } }
            String key = root + "|" + message.replaceAll("\\b\\d+\\b", "<n>"); StackGroup previous = groups.get(key);
            groups.put(key, previous == null ? new StackGroup(root, message, applicationFrame, 1, item.source().toString()) : previous.increment()); }
        StringBuilder out = new StringBuilder("STACK-TRACE INTELLIGENCE\n\n"); groups.values().stream().sorted(Comparator.comparingInt(StackGroup::count).reversed()).forEach(group ->
            out.append(group.count()).append("× ").append(group.root()).append(group.message().isBlank() ? "" : ": " + group.message()).append("\n  first application frame: ").append(blank(group.frame())).append("\n  source: ").append(group.source()).append("\n\n"));
        return out.append(groups.isEmpty() ? "No grouped Java exceptions found.\n" : "Unique exception groups: " + groups.size() + "\n").toString();
    }

    public String correlations(List<DiagnosticEvent> events) {
        Map<String, List<DiagnosticEvent>> traces = events.stream().filter(item -> !item.event().traceId().isBlank()).collect(java.util.stream.Collectors.groupingBy(item -> item.event().traceId(), LinkedHashMap::new, java.util.stream.Collectors.toList()));
        StringBuilder out = new StringBuilder("CROSS-SERVICE CORRELATION\n\n"); traces.entrySet().stream().sorted(Map.Entry.<String,List<DiagnosticEvent>>comparingByValue(Comparator.comparingInt(List::size)).reversed()).limit(500).forEach(entry -> {
            List<DiagnosticEvent> items = entry.getValue().stream().sorted(Comparator.comparing(DiagnosticEvent::timestamp)).toList(); long duration = java.time.Duration.between(items.getFirst().timestamp(), items.getLast().timestamp()).toMillis();
            List<String> sources = items.stream().map(item -> item.source().getFileName().toString()).distinct().toList(); long errors = items.stream().filter(item -> item.event().level().atLeast(LogLevel.ERROR)).count();
            out.append(entry.getKey()).append(" — ").append(items.size()).append(" events, ").append(sources.size()).append(" source(s), ").append(duration).append(" ms, ").append(errors).append(" error(s)\n  ").append(String.join(" → ", sources)).append("\n"); });
        return out.append(traces.isEmpty() ? "No trace/correlation IDs were detected.\n" : "\nCorrelated journeys: " + traces.size() + "\n").toString();
    }

    public String performance(List<DiagnosticEvent> events) {
        List<Double> durations = new ArrayList<>(); Map<String, List<Double>> operations = new LinkedHashMap<>();
        for (DiagnosticEvent item : events) { Matcher matcher = DURATION.matcher(item.event().content()); while (matcher.find()) { double value = Double.parseDouble(matcher.group(1)); String unit = matcher.group(2).toLowerCase(Locale.ROOT); if (unit.equals("s")) value *= 1000; else if (unit.startsWith("u") || unit.startsWith("µ")) value /= 1000; durations.add(value); String operation = item.event().summary().replaceAll("\\b\\d+(?:\\.\\d+)?\\s*(?:ms|s|us|µs)\\b", "<duration>"); operations.computeIfAbsent(operation, ignored -> new ArrayList<>()).add(value); } }
        durations.sort(Double::compare); StringBuilder out = new StringBuilder("PERFORMANCE INVESTIGATION\n\nSamples: ").append(durations.size()).append('\n');
        if (!durations.isEmpty()) out.append("p50: ").append(percentile(durations, .50)).append(" ms\np95: ").append(percentile(durations, .95)).append(" ms\np99: ").append(percentile(durations, .99)).append(" ms\nmax: ").append(durations.getLast()).append(" ms\n\nSlow patterns:\n");
        operations.entrySet().stream().sorted(Comparator.comparingDouble((Map.Entry<String,List<Double>> entry) -> entry.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0)).reversed()).limit(20).forEach(entry -> out.append(String.format(Locale.ROOT, "  %.2f ms avg (%d×) %s%n", entry.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0), entry.getValue().size(), entry.getKey())));
        return out.toString();
    }

    public String quality(List<DiagnosticEvent> events) {
        Map<String, Long> patterns = events.stream().collect(java.util.stream.Collectors.groupingBy(item -> item.event().summary().replaceAll("\\b[0-9a-fA-F-]{8,}\\b|\\b\\d+\\b", "<value>"), java.util.stream.Collectors.counting()));
        long unknown = events.stream().filter(item -> item.event().level() == LogLevel.UNKNOWN).count(); long missingTrace = events.stream().filter(item -> item.event().traceId().isBlank()).count();
        StringBuilder out = new StringBuilder("LOGGING HEALTH\n\nEvents: ").append(events.size()).append("\nUnknown severity: ").append(unknown).append("\nMissing trace ID: ").append(missingTrace).append("\n\nNoisiest patterns:\n");
        patterns.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed()).limit(20).forEach(entry -> out.append(entry.getValue()).append("× ").append(entry.getKey()).append('\n'));
        out.append("\nRecommendations:\n"); if (missingTrace > events.size() / 3) out.append("- Add traceId/spanId to structured logging context.\n"); if (unknown > events.size() / 10) out.append("- Standardize severity fields and timestamp layout.\n"); if (patterns.values().stream().anyMatch(count -> count > 100)) out.append("- Rate-limit or aggregate noisy repeated messages.\n"); return out.toString();
    }

    public String threadDump(String content) {
        Map<String, Long> states = STATUS.matcher(content).results().collect(java.util.stream.Collectors.groupingBy(result -> result.group(1), java.util.stream.Collectors.counting()));
        long threads = content.lines().filter(line -> line.startsWith("\"")).count(); long deadlocks = content.lines().filter(line -> line.toLowerCase(Locale.ROOT).contains("deadlock")).count();
        StringBuilder out = new StringBuilder("THREAD-DUMP ANALYSIS\n\nThreads: ").append(threads).append("\nDeadlock indicators: ").append(deadlocks).append("\nStates:\n"); states.forEach((state,count) -> out.append("  ").append(state).append(": ").append(count).append('\n'));
        out.append("\nRisk signals:\n"); if (states.getOrDefault("BLOCKED",0L) > 5) out.append("- Many BLOCKED threads; inspect monitor ownership and lock chains.\n"); if (states.getOrDefault("WAITING",0L) > Math.max(20, threads * 3 / 4)) out.append("- Most threads are waiting; check pool starvation or stalled dependencies.\n"); if (deadlocks > 0) out.append("- JVM reported a possible deadlock.\n"); return out.toString();
    }

    public String gc(String content) {
        List<Double> pauses = GC_PAUSE.matcher(content).results().map(result -> Double.parseDouble(result.group(1))).sorted().toList();
        StringBuilder out = new StringBuilder("GC / JVM ANALYSIS\n\nGC pauses: ").append(pauses.size()).append('\n'); if (!pauses.isEmpty()) out.append("p95: ").append(percentile(pauses,.95)).append(" ms\nmax: ").append(pauses.getLast()).append(" ms\nTotal pause: ").append(pauses.stream().mapToDouble(Double::doubleValue).sum()).append(" ms\n");
        if (pauses.stream().anyMatch(value -> value > 200)) out.append("\nWarning: pauses above 200 ms detected; correlate these timestamps with latency/error spikes.\n"); return out.toString();
    }

    private static double percentile(List<Double> values, double percentile) { return values.get(Math.min(values.size()-1, Math.max(0, (int)Math.ceil(percentile * values.size()) - 1))); }
    private static String blank(String value) { return value.isBlank() ? "not identified" : value; }
    private record StackGroup(String root, String message, String frame, int count, String source) { StackGroup increment() { return new StackGroup(root,message,frame,count+1,source); } }
}
