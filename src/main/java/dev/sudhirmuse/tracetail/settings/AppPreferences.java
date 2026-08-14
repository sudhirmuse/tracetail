/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.settings;

import java.util.ArrayList;
import java.util.List;

public final class AppPreferences {
    public boolean wrapLines;
    public boolean alwaysOnTop;
    public boolean reopenRecentFiles = true;
    public double fontSize = 12;
    public int tabWidth = 4;
    public String charset = "UTF-8";
    public String tabSide = "TOP";
    public String theme = "DARK";
    public String scratchDirectory;
    public List<String> recentFiles = new ArrayList<>();
    public List<SavedSearch> savedSearches = new ArrayList<>();
    public List<HighlightRule> highlightRules = defaultRules();
    public List<Bookmark> bookmarks = new ArrayList<>();
    public List<AlertRule> alertRules = new ArrayList<>();

    public static List<HighlightRule> defaultRules() {
        return new ArrayList<>(List.of(
            new HighlightRule("Errors", "ERROR|FATAL|Exception", true, true, false, "#5b1f2a", "#ffd7dd", true, false, true),
            new HighlightRule("Warnings", "WARN(?:ING)?", true, true, false, "#5a4615", "#fff0b3", true, false, true)
        ));
    }

    public record SavedSearch(String name, String expression, boolean regex) { }
    public record HighlightRule(String name, String expression, boolean regex, boolean ignoreCase, boolean invert,
                                String background, String foreground, boolean bold, boolean italic, boolean enabled) { }
    public record Bookmark(String file, long lineNumber, String note, String preview, String createdAt) { }
    public record AlertRule(String expression, boolean regex, boolean ignoreCase, boolean autoScratch, boolean enabled) { }
}
