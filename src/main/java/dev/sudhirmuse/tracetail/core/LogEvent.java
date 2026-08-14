/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import java.time.Instant;

public record LogEvent(long sequence, long lineNumber, Instant receivedAt, LogLevel level, String threadId, String traceId,
                       String summary, String content) {}
