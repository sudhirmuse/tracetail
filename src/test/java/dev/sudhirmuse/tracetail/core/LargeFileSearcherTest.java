/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class LargeFileSearcherTest {
    @Test void searchesEveryPageWithBoundedResults(@TempDir Path directory) throws Exception {
        Path log = directory.resolve("search.log");
        StringBuilder content = new StringBuilder();
        for (int line = 0; line < 1_000; line++) content.append(line % 100 == 0 ? "ERROR " : "INFO ").append(line).append('\n');
        Files.writeString(log, content);
        try (SparseLineIndex index = new SparseLineIndex(log, StandardCharsets.UTF_8, 10, ignored -> { })) {
            index.start();
            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> { while (!index.snapshot().complete()) Thread.onSpinWait(); });
            PagedLineReader reader = new PagedLineReader(log, StandardCharsets.UTF_8, index, 50);
            LargeFileSearcher.Result result = new LargeFileSearcher(reader, index.snapshot().lineCount(), 50)
                .search(SearchPattern.compile("ERROR", false), 5, new AtomicBoolean(), ignored -> { });
            assertEquals(5, result.matches().size());
            assertTrue(result.truncated());
            assertEquals(0, result.matches().getFirst().lineNumber());
        }
    }
}
