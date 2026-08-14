/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public record EventFilter(String query, boolean regularExpression, LogLevel minimumLevel, String traceId) {
    public Predicate<LogEvent> predicate() {
        Pattern regex = regularExpression && query != null && !query.isBlank()
            ? Pattern.compile(query, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE) : null;
        String text = query == null ? "" : query.toLowerCase(Locale.ROOT);
        String requiredTrace = traceId == null ? "" : traceId.strip();
        return event -> event.level().atLeast(minimumLevel)
            && (requiredTrace.isEmpty() || event.traceId().equalsIgnoreCase(requiredTrace))
            && (text.isEmpty() || (regex != null ? regex.matcher(event.content()).find() : event.content().toLowerCase(Locale.ROOT).contains(text)));
    }

    public static boolean validRegex(String value) {
        try { Pattern.compile(value); return true; }
        catch (PatternSyntaxException exception) { return false; }
    }
}
