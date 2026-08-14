/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail;

import dev.sudhirmuse.tracetail.core.LogEvent;
import java.nio.file.Path;
import java.time.Instant;

record InvestigationEvent(Path source, Instant timestamp, LogEvent event) { }
