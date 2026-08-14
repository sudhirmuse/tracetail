/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class FileTailer implements AutoCloseable {
    private static final long INITIAL_BYTES = 128L * 1024;
    private static final int MAX_READ_BYTES = 128 * 1024;
    private final Path path;
    private final Consumer<List<String>> consumer;
    private final Consumer<Exception> errorConsumer;
    private final ScheduledExecutorService executor;
    private long position;
    private byte[] partial = new byte[0];
    private byte[] boundary = new byte[0];
    private Object fileKey;
    private volatile boolean initialized;
    private final Charset charset;
    private final byte[] newline;

    public FileTailer(Path path, Consumer<List<String>> consumer, Consumer<Exception> errorConsumer) {
        this(path, consumer, errorConsumer, StandardCharsets.UTF_8);
    }

    public FileTailer(Path path, Consumer<List<String>> consumer, Consumer<Exception> errorConsumer, Charset charset) {
        this.path = path.toAbsolutePath().normalize();
        this.consumer = consumer;
        this.errorConsumer = errorConsumer;
        this.charset = charset;
        this.newline = "\n".getBytes(charset);
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "tracetail-" + path.getFileName());
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() throws IOException {
        if (!Files.isRegularFile(path)) throw new IOException("Not a readable file: " + path);
        executor.execute(this::initializeAndPoll);
        executor.scheduleWithFixedDelay(this::pollSafely, 75, 75, TimeUnit.MILLISECONDS);
    }

    private void initializeAndPoll() {
        try {
        long size = Files.size(path);
        fileKey = fileKey();
        position = Math.max(0, size - INITIAL_BYTES);
        if (position > 0) skipPartialFirstLine();
            initialized = true;
            poll();
        } catch (Exception exception) { errorConsumer.accept(exception); }
    }

    private void skipPartialFirstLine() throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            file.seek(position);
            int matched = 0;
            while (file.getFilePointer() < file.length()) {
                int value = file.read();
                if (value < 0) break;
                if ((byte) value == newline[matched]) {
                    matched++;
                    if (matched == newline.length) break;
                } else matched = (byte) value == newline[0] ? 1 : 0;
            }
            position = file.getFilePointer();
        }
    }

    private void pollSafely() {
        if (!initialized) return;
        try { poll(); }
        catch (Exception exception) { errorConsumer.accept(exception); }
    }

    void poll() throws IOException {
        if (!Files.exists(path)) return;
        Object currentKey = fileKey();
        boolean identityChanged = fileKey != null && currentKey != null && !fileKey.equals(currentKey);
        boolean prefixChanged = position > 0 && boundary.length > 0 && sizeAtLeast(position)
            && !java.util.Arrays.equals(boundary, readBoundary(position));
        if (identityChanged || prefixChanged) {
            position = 0;
            partial = new byte[0];
            boundary = new byte[0];
        }
        fileKey = currentKey;
        long size = Files.size(path);
        if (size < position) { position = 0; partial = new byte[0]; boundary = new byte[0]; }
        if (size == position) return;
        byte[] bytes;
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            file.seek(position);
            int length = (int) Math.min(MAX_READ_BYTES, size - position);
            bytes = new byte[length];
            file.readFully(bytes);
            position = file.getFilePointer();
        }
        boundary = readBoundary(position);
        consumeBytes(bytes);
    }

    private Object fileKey() throws IOException {
        return Files.readAttributes(path, BasicFileAttributes.class).fileKey();
    }

    private boolean sizeAtLeast(long offset) throws IOException { return Files.size(path) >= offset; }

    private byte[] readBoundary(long offset) throws IOException {
        int length = (int) Math.min(64, offset);
        if (length == 0) return new byte[0];
        byte[] result = new byte[length];
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            file.seek(offset - length);
            file.readFully(result);
        }
        return result;
    }

    private void consumeBytes(byte[] bytes) {
        byte[] combined = new byte[partial.length + bytes.length];
        System.arraycopy(partial, 0, combined, 0, partial.length);
        System.arraycopy(bytes, 0, combined, partial.length, bytes.length);
        List<String> lines = new ArrayList<>();
        int start = 0;
        for (int index = 0; index <= combined.length - newline.length; index++) {
            if (matches(combined, index, newline)) {
                String line = new String(combined, start, index - start, charset);
                if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);
                lines.add(line);
                start = index + newline.length;
                index += newline.length - 1;
            }
        }
        partial = java.util.Arrays.copyOfRange(combined, start, combined.length);
        if (!lines.isEmpty()) consumer.accept(List.copyOf(lines));
    }

    private boolean matches(byte[] bytes, int offset, byte[] expected) {
        for (int index = 0; index < expected.length; index++) {
            if (bytes[offset + index] != expected[index]) return false;
        }
        return true;
    }

    @Override public void close() { executor.shutdownNow(); }
}
