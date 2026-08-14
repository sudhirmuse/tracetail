/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;
import java.util.List;
public record ParserProfile(String name, String timestampRegex, String levelRegex, String threadRegex, String traceRegex,
                            List<String> columns) { }
