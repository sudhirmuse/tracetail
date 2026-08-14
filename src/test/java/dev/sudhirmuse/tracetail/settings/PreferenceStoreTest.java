/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.settings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PreferenceStoreTest {
    @Test void roundTripsPortableJsonPreferences(@TempDir Path directory) throws Exception {
        PreferenceStore store = new PreferenceStore(directory.resolve("preferences.json"));
        AppPreferences preferences = new AppPreferences();
        preferences.fontSize = 15;
        preferences.recentFiles.add("application.log");
        preferences.savedSearches.add(new AppPreferences.SavedSearch("Failures", "ERROR", false));
        store.save(preferences);
        AppPreferences loaded = store.load();
        assertEquals(15, loaded.fontSize);
        assertEquals(java.util.List.of("application.log"), loaded.recentFiles);
        assertEquals("Failures", loaded.savedSearches.getFirst().name());
    }

    @Test void migratesValuesEnteredInTheFormerAmbiguousNameField(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("preferences.json");
        Files.writeString(file, """
            {"highlightRules":[{"name":"45084","expression":"ERROR|FATAL|Exception","regex":true,
            "ignoreCase":false,"invert":false,"background":"#FFFF00","foreground":"#000000",
            "bold":false,"italic":false,"enabled":true}]}
            """);
        AppPreferences.HighlightRule rule = new PreferenceStore(file).load().highlightRules.getFirst();
        assertEquals("45084", rule.expression());
        assertEquals(false, rule.regex());
        assertEquals(true, rule.ignoreCase());
    }
}
