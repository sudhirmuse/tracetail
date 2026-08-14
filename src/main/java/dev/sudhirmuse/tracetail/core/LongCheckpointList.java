/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

/** Chunked primitive long storage; avoids boxed Long objects and large contiguous array copies. */
final class LongCheckpointList {
    private static final int CHUNK_SHIFT = 12;
    private static final int CHUNK_SIZE = 1 << CHUNK_SHIFT;
    private static final int CHUNK_MASK = CHUNK_SIZE - 1;
    private long[][] chunks = new long[4][];
    private int size;

    void add(long value) {
        int chunk = size >> CHUNK_SHIFT;
        if (chunk == chunks.length) chunks = java.util.Arrays.copyOf(chunks, chunks.length * 2);
        if (chunks[chunk] == null) chunks[chunk] = new long[CHUNK_SIZE];
        chunks[chunk][size & CHUNK_MASK] = value;
        size++;
    }

    long get(int index) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException(index);
        return chunks[index >> CHUNK_SHIFT][index & CHUNK_MASK];
    }

    int size() { return size; }
}
