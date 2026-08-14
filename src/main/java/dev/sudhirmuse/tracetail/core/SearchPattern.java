/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Compiles a case-insensitive text or regular-expression search and reports match ranges. */
public final class SearchPattern {
    private final String query;
    private final Pattern pattern;
    private final boolean valid;

    private SearchPattern(String query, Pattern pattern, boolean valid) {
        this.query = query;
        this.pattern = pattern;
        this.valid = valid;
    }

    public static SearchPattern compile(String query, boolean regex) {
        return compile(query, regex, true);
    }

    public static SearchPattern compile(String query, boolean regex, boolean ignoreCase) {
        String value = query == null ? "" : query;
        if (value.isBlank()) return new SearchPattern(value, null, true);
        try {
            String expression = regex ? value : Pattern.quote(value);
            int flags = ignoreCase ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0;
            return new SearchPattern(value, Pattern.compile(expression, flags), true);
        } catch (PatternSyntaxException exception) {
            return new SearchPattern(value, null, false);
        }
    }

    public boolean valid() { return valid; }
    public boolean empty() { return query.isBlank(); }

    public boolean matches(String text) {
        return valid && pattern != null && pattern.matcher(text == null ? "" : text).find();
    }

    public List<Range> ranges(String text) {
        if (!valid || pattern == null || text == null) return List.of();
        List<Range> ranges = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            if (matcher.start() == matcher.end()) continue;
            ranges.add(new Range(matcher.start(), matcher.end()));
        }
        return List.copyOf(ranges);
    }

    public List<String> groups(String text) {
        if (!valid || pattern == null || text == null) return List.of();
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find() || matcher.groupCount() == 0) return List.of();
        List<String> groups = new ArrayList<>(matcher.groupCount());
        for (int index = 1; index <= matcher.groupCount(); index++) {
            String value = matcher.group(index);
            groups.add(value == null ? "" : value);
        }
        return List.copyOf(groups);
    }

    public record Range(int start, int end) { }
}
