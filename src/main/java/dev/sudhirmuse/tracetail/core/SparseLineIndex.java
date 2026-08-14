/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Asynchronously indexes sparse line checkpoints using 64-bit byte offsets and bounded scan buffers. */
public final class SparseLineIndex implements AutoCloseable {
    public static final int DEFAULT_STRIDE = 1_024;
    private static final int SCAN_BUFFER_BYTES = 4 * 1024 * 1024;
    private final Path path;
    private final int stride;
    private final byte[] delimiter;
    private final LongCheckpointList checkpoints = new LongCheckpointList();
    private final ExecutorService worker;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final Consumer<Snapshot> progress;
    private volatile long indexedBytes;
    private volatile long fileSize;
    private volatile long lineCount;
    private volatile boolean complete;
    private volatile IOException failure;

    public SparseLineIndex(Path path, Charset charset, Consumer<Snapshot> progress) {
        this(path, charset, DEFAULT_STRIDE, progress);
    }

    SparseLineIndex(Path path, Charset charset, int stride, Consumer<Snapshot> progress) {
        if (stride < 1) throw new IllegalArgumentException("stride must be positive");
        this.path = path.toAbsolutePath().normalize();
        this.stride = stride;
        this.delimiter = "\n".getBytes(charset);
        this.progress = progress == null ? ignored -> { } : progress;
        checkpoints.add(0);
        worker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "tracetail-index-" + path.getFileName());
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() { worker.execute(this::scanSafely); }

    private void scanSafely() {
        try { scan(); }
        catch (IOException exception) { failure = exception; progress.accept(snapshot()); }
    }

    private void scan() throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            fileSize = channel.size();
            ByteBuffer buffer = ByteBuffer.allocateDirect(SCAN_BUFFER_BYTES);
            int matched = 0;
            long absolute = 0;
            long lines = 0;
            while (!cancelled.get() && channel.read(buffer) >= 0) {
                buffer.flip();
                while (buffer.hasRemaining()) {
                    byte value = buffer.get();
                    absolute++;
                    if (value == delimiter[matched]) {
                        matched++;
                        if (matched == delimiter.length) {
                            matched = 0;
                            lines++;
                            if (lines % stride == 0) addCheckpoint(absolute);
                        }
                    } else matched = value == delimiter[0] ? 1 : 0;
                }
                buffer.clear();
                indexedBytes = absolute;
                lineCount = lines;
                progress.accept(snapshot());
            }
            indexedBytes = absolute;
            lineCount = lines + (fileSize > 0 && !endsWithDelimiter(channel) ? 1 : 0);
            complete = !cancelled.get();
            progress.accept(snapshot());
        }
    }

    private boolean endsWithDelimiter(FileChannel channel) throws IOException {
        if (fileSize < delimiter.length) return false;
        ByteBuffer tail = ByteBuffer.allocate(delimiter.length);
        channel.read(tail, fileSize - delimiter.length);
        return java.util.Arrays.equals(tail.array(), delimiter);
    }

    private synchronized void addCheckpoint(long offset) { checkpoints.add(offset); }

    public synchronized Checkpoint checkpointForLine(long requestedLine) {
        long safeLine = Math.max(0, Math.min(requestedLine, Math.max(0, lineCount - 1)));
        long checkpointNumber = safeLine / stride;
        int index = (int) Math.min(checkpointNumber, checkpoints.size() - 1L);
        return new Checkpoint((long) index * stride, checkpoints.get(index));
    }

    public Snapshot snapshot() {
        return new Snapshot(fileSize, indexedBytes, lineCount, checkpointCount(), complete, failure);
    }

    private synchronized int checkpointCount() { return checkpoints.size(); }
    public int stride() { return stride; }
    @Override public void close() { cancelled.set(true); worker.shutdownNow(); }

    public record Checkpoint(long lineNumber, long byteOffset) { }
    public record Snapshot(long fileSize, long indexedBytes, long lineCount, int checkpoints, boolean complete, IOException failure) {
        public double progress() { return fileSize == 0 ? 1 : Math.min(1, (double) indexedBytes / fileSize); }
    }
}
