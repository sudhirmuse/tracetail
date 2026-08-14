/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class SparseLineIndexTest {
    @Test void indexesCheckpointsAndReadsPages(@TempDir Path directory) throws Exception {
        Path log = directory.resolve("indexed.log");
        StringBuilder content = new StringBuilder();
        for (int line = 0; line < 5_000; line++) content.append("line-").append(line).append('\n');
        Files.writeString(log, content, StandardCharsets.UTF_8);
        try (SparseLineIndex index = new SparseLineIndex(log, StandardCharsets.UTF_8, ignored -> { })) {
            index.start();
            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
                while (!index.snapshot().complete()) Thread.onSpinWait();
            });
            assertEquals(5_000, index.snapshot().lineCount());
            assertTrue(index.snapshot().checkpoints() >= 5);
            SparseLineIndex.Checkpoint checkpoint = index.checkpointForLine(4_500);
            assertEquals(4_096, checkpoint.lineNumber());
            PagedLineReader reader = new PagedLineReader(log, StandardCharsets.UTF_8, index, 100);
            PagedLineReader.Page page = reader.readPage(4_523);
            assertEquals(4_500, page.startLine());
            assertEquals("line-4523", page.lines().get(23).text());
        }
    }

    @Test void primitiveCheckpointsPreserveOffsetsBeyondTwoGigabytes() {
        LongCheckpointList values = new LongCheckpointList();
        values.add(0);
        values.add(3L * 1024 * 1024 * 1024);
        assertEquals(3_221_225_472L, values.get(1));
    }

    @Test void pageCacheRemainsBounded(@TempDir Path directory) throws Exception {
        Path log = directory.resolve("cache.log");
        StringBuilder content = new StringBuilder();
        for (int line = 0; line < 2_000; line++) content.append(line).append('\n');
        Files.writeString(log, content);
        try (SparseLineIndex index = new SparseLineIndex(log, StandardCharsets.UTF_8, 10, ignored -> { })) {
            index.start();
            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> { while (!index.snapshot().complete()) Thread.onSpinWait(); });
            PagedLineReader reader = new PagedLineReader(log, StandardCharsets.UTF_8, index, 10);
            for (int line = 0; line < 200; line += 10) reader.readPage(line);
            assertEquals(8, reader.cachedPages());
        }
    }
}
