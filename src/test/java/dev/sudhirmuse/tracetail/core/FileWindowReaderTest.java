/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileWindowReaderTest {
    @Test void readsAWindowNearAnyFilePosition(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("large.log");
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < 300_000; index++) text.append("INFO line ").append(index).append('\n');
        Files.writeString(file, text, StandardCharsets.UTF_8);
        FileWindowReader.Window window = new FileWindowReader().read(file, 0.5, StandardCharsets.UTF_8);
        assertTrue(window.startOffset() > 0);
        assertTrue(window.lines().stream().anyMatch(line -> line.startsWith("INFO line")));
        assertTrue(window.endOffset() <= window.fileSize());
    }

    @Test void rendersEmbeddedNullsSafely(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("binary.log");
        Files.write(file, new byte[]{'a', 0, 'b', '\n'});
        assertEquals("a␀b", new FileWindowReader().read(file, 0, StandardCharsets.UTF_8).lines().getFirst());
    }
}
