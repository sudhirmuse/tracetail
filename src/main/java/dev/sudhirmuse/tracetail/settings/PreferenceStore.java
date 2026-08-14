/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class PreferenceStore {
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final Path path;

    public PreferenceStore(Path path) { this.path = path.toAbsolutePath().normalize(); }

    public static PreferenceStore userDefault() {
        Path directory = Path.of(System.getProperty("user.home"), ".tracetail");
        return new PreferenceStore(directory.resolve("preferences.json"));
    }

    public AppPreferences load() {
        if (!Files.isRegularFile(path)) return new AppPreferences();
        try {
            AppPreferences preferences = mapper.readValue(path.toFile(), AppPreferences.class);
            migrateLegacyHighlightRules(preferences);
            return preferences;
        }
        catch (IOException exception) { return new AppPreferences(); }
    }

    private void migrateLegacyHighlightRules(AppPreferences preferences) {
        if (preferences.highlightRules == null) preferences.highlightRules = AppPreferences.defaultRules();
        if (preferences.bookmarks == null) preferences.bookmarks = new java.util.ArrayList<>();
        if (preferences.alertRules == null) preferences.alertRules = new java.util.ArrayList<>();
        for (int index = 0; index < preferences.highlightRules.size(); index++) {
            AppPreferences.HighlightRule rule = preferences.highlightRules.get(index);
            boolean defaultExpression = rule.expression().equals("ERROR|FATAL|Exception") || rule.expression().equals("WARN(?:ING)?");
            boolean defaultName = rule.name().equals("Errors") || rule.name().equals("Warnings");
            if (defaultExpression && !defaultName && !rule.name().isBlank()) {
                preferences.highlightRules.set(index, new AppPreferences.HighlightRule(rule.name(), rule.name(), false, true,
                    rule.invert(), rule.background(), rule.foreground(), rule.bold(), rule.italic(), rule.enabled()));
            }
        }
    }

    public void save(AppPreferences preferences) throws IOException {
        Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        mapper.writeValue(temporary.toFile(), preferences);
        try { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (IOException exception) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
    }

    public void exportTo(Path destination, AppPreferences preferences) throws IOException {
        mapper.writeValue(destination.toFile(), preferences);
    }

    public AppPreferences importFrom(Path source) throws IOException {
        return mapper.readValue(source.toFile(), AppPreferences.class);
    }

    public Path path() { return path; }
}
