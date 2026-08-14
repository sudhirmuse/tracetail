/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LaunchOptionsTest {
    @Test void parsesWindowAndMultipleFileOptions() {
        LaunchOptions options = LaunchOptions.parse(List.of("--window-position", "10", "20", "900", "600", "--window-state", "2", "a.log", "b.log"));
        assertEquals(2, options.files().size());
        assertEquals(900, options.position().width());
        assertEquals(LaunchOptions.WindowState.MAXIMIZED, options.state());
    }

    @Test void rejectsUnknownOptions() {
        assertThrows(IllegalArgumentException.class, () -> LaunchOptions.parse(List.of("--wat")));
    }
}
