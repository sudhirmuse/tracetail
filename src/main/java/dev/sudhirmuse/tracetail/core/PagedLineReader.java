/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads line pages from disk using sparse checkpoints and retains only a bounded LRU cache. */
public final class PagedLineReader {
    private static final int DEFAULT_CACHE_PAGES = 8;
    private final Path path;
    private final Charset charset;
    private final byte[] delimiter;
    private final SparseLineIndex index;
    private final int pageLines;
    private final Map<Long, Page> cache;

    public PagedLineReader(Path path, Charset charset, SparseLineIndex index, int pageLines) {
        if (pageLines < 1) throw new IllegalArgumentException("pageLines must be positive");
        this.path = path.toAbsolutePath().normalize();
        this.charset = charset;
        this.delimiter = "\n".getBytes(charset);
        this.index = index;
        this.pageLines = pageLines;
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<Long, Page> eldest) { return size() > DEFAULT_CACHE_PAGES; }
        };
    }

    public synchronized Page readPage(long requestedLine) throws IOException {
        long pageStart = Math.max(0, requestedLine - Math.floorMod(requestedLine, pageLines));
        Page cached = cache.get(pageStart);
        long currentSize = java.nio.file.Files.size(path);
        if (cached != null && cached.fileSize() == currentSize) return cached;
        Page page = load(pageStart, currentSize);
        cache.put(pageStart, page);
        return page;
    }

    private Page load(long pageStart, long fileSize) throws IOException {
        SparseLineIndex.Checkpoint checkpoint = index.checkpointForLine(pageStart);
        List<Line> lines = new ArrayList<>(pageLines);
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            file.seek(checkpoint.byteOffset());
            long lineNumber = checkpoint.lineNumber();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(256);
            int matched = 0;
            long lineOffset = file.getFilePointer();
            while (file.getFilePointer() < file.length() && lines.size() < pageLines) {
                int value = file.read();
                if (value < 0) break;
                byte current = (byte) value;
                if (current == delimiter[matched]) {
                    matched++;
                    if (matched == delimiter.length) {
                        if (lineNumber >= pageStart) lines.add(new Line(lineNumber, lineOffset, decode(bytes.toByteArray())));
                        lineNumber++;
                        lineOffset = file.getFilePointer();
                        bytes.reset();
                        matched = 0;
                    }
                } else {
                    if (matched > 0) bytes.write(delimiter, 0, matched);
                    if (current == delimiter[0]) matched = 1;
                    else { matched = 0; bytes.write(current); }
                }
            }
            if (lines.size() < pageLines && bytes.size() > 0 && lineNumber >= pageStart)
                lines.add(new Line(lineNumber, lineOffset, decode(bytes.toByteArray())));
        }
        return new Page(pageStart, fileSize, List.copyOf(lines));
    }

    private String decode(byte[] bytes) {
        String value = new String(bytes, charset);
        if (value.endsWith("\r")) value = value.substring(0, value.length() - 1);
        return value.replace("\u0000", "␀");
    }

    public synchronized int cachedPages() { return cache.size(); }
    public record Line(long lineNumber, long byteOffset, String text) { }
    public record Page(long startLine, long fileSize, List<Line> lines) { }
}
