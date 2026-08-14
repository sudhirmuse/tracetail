/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;
import java.nio.file.Path;
import java.time.Instant;
public record DiagnosticEvent(Path source, Instant timestamp, LogEvent event) { }
