/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileTailerTest {
    @TempDir Path temporary;

    @Test void emitsOnlyCompleteLinesAndRecoversFromTruncation() throws Exception {
        Path log = temporary.resolve("application.log");
        Files.writeString(log, "first\npartial");
        List<String> lines = new ArrayList<>();
        try (FileTailer tailer = new FileTailer(log, lines::addAll, exception -> { throw new AssertionError(exception); })) {
            tailer.poll();
            assertEquals(List.of("first"), lines);
            Files.writeString(log, "-end\nsecond\r\n", StandardOpenOption.APPEND);
            tailer.poll();
            assertEquals(List.of("first", "partial-end", "second"), lines);
            Files.writeString(log, "rotated\n");
            tailer.poll();
            assertEquals(List.of("first", "partial-end", "second", "rotated"), lines);
            Files.delete(log);
            Files.writeString(log, "replacement-file-that-is-longer-than-before\n");
            tailer.poll();
            assertEquals("replacement-file-that-is-longer-than-before", lines.getLast());
        }
    }
}
