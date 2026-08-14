/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Reads a bounded text window around any byte position without loading the whole file. */
public final class FileWindowReader {
    private static final int DEFAULT_WINDOW = 2 * 1024 * 1024;

    public Window read(Path path, double fraction, Charset charset) throws IOException {
        if (fraction < 0 || fraction > 1) throw new IllegalArgumentException("fraction must be between 0 and 1");
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            long size = file.length();
            if (size == 0) return new Window(0, 0, 0, List.of());
            long desired = Math.min(size - 1, Math.max(0, (long) (size * fraction)));
            long start = Math.max(0, desired - DEFAULT_WINDOW / 2L);
            byte[] delimiter = "\n".getBytes(charset);
            if (start > 0) start = findAfterNextDelimiter(file, start, size, delimiter);
            file.seek(start);
            int length = (int) Math.min(DEFAULT_WINDOW, size - start);
            byte[] bytes = new byte[length];
            file.readFully(bytes);
            int end = bytes.length;
            if (start + end < size) {
                end = lastDelimiterEnd(bytes, delimiter);
            }
            String text = sanitize(new String(bytes, 0, end, charset));
            List<String> lines = Arrays.asList(text.split("\\R", -1));
            if (!lines.isEmpty() && lines.getLast().isEmpty()) lines = lines.subList(0, lines.size() - 1);
            return new Window(size, start, start + end, List.copyOf(lines));
        }
    }

    private long findAfterNextDelimiter(RandomAccessFile file, long start, long size, byte[] delimiter) throws IOException {
        file.seek(start);
        int matched = 0;
        while (file.getFilePointer() < size) {
            int value = file.read();
            if (value < 0) break;
            if ((byte) value == delimiter[matched]) {
                matched++;
                if (matched == delimiter.length) return file.getFilePointer();
            } else matched = (byte) value == delimiter[0] ? 1 : 0;
        }
        return size;
    }

    private int lastDelimiterEnd(byte[] bytes, byte[] delimiter) {
        for (int offset = bytes.length - delimiter.length; offset >= 0; offset--) {
            boolean matches = true;
            for (int index = 0; index < delimiter.length; index++) {
                if (bytes[offset + index] != delimiter[index]) { matches = false; break; }
            }
            if (matches) return offset + delimiter.length;
        }
        return bytes.length;
    }

    private String sanitize(String value) {
        StringBuilder result = new StringBuilder(value.length());
        value.codePoints().forEach(codePoint -> {
            if (codePoint == 0) result.append('␀');
            else if (Character.isISOControl(codePoint) && codePoint != '\n' && codePoint != '\r' && codePoint != '\t')
                result.append(String.format("\\x%02X", codePoint));
            else result.appendCodePoint(codePoint);
        });
        return result.toString();
    }

    public record Window(long fileSize, long startOffset, long endOffset, List<String> lines) { }
}
