/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.DoubleConsumer;

/** Fixed-memory whole-file search over disk-backed pages with cooperative cancellation. */
public final class LargeFileSearcher {
    private final PagedLineReader reader;
    private final long totalLines;
    private final int pageLines;

    public LargeFileSearcher(PagedLineReader reader, long totalLines, int pageLines) {
        this.reader = reader;
        this.totalLines = Math.max(0, totalLines);
        this.pageLines = pageLines;
    }

    public Result search(SearchPattern pattern, int maximumResults, AtomicBoolean cancelled, DoubleConsumer progress) throws IOException {
        if (!pattern.valid() || pattern.empty()) throw new IllegalArgumentException("A valid non-empty search is required");
        List<PagedLineReader.Line> matches = new ArrayList<>();
        boolean truncated = false;
        for (long start = 0; start < totalLines && !cancelled.get(); start += pageLines) {
            PagedLineReader.Page page = reader.readPage(start);
            if (page.lines().isEmpty()) break;
            for (PagedLineReader.Line line : page.lines()) {
                if (pattern.matches(line.text())) {
                    if (matches.size() == maximumResults) { truncated = true; break; }
                    matches.add(line);
                }
            }
            progress.accept(totalLines == 0 ? 1 : Math.min(1, (double) (start + page.lines().size()) / totalLines));
            if (truncated) break;
        }
        return new Result(List.copyOf(matches), truncated, cancelled.get());
    }

    public record Result(List<PagedLineReader.Line> matches, boolean truncated, boolean cancelled) { }
}
