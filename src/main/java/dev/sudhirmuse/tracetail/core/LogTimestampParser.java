/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LogTimestampParser {
    private static final Pattern ISO = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?Z)\\b");
    private static final Pattern LOCAL = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d{1,9})?)\\b");
    private static final List<DateTimeFormatter> LOCAL_FORMATS = List.of(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSSSS"), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    );

    public Optional<Instant> parse(String text) {
        if (text == null) return Optional.empty();
        Matcher iso = ISO.matcher(text);
        if (iso.find()) try { return Optional.of(Instant.parse(iso.group(1))); } catch (DateTimeParseException ignored) { }
        Matcher local = LOCAL.matcher(text);
        if (!local.find()) return Optional.empty();
        String value = local.group(1).replace(',', '.');
        for (DateTimeFormatter formatter : LOCAL_FORMATS) {
            try { return Optional.of(LocalDateTime.parse(value, formatter).atZone(ZoneId.systemDefault()).toInstant()); }
            catch (DateTimeParseException ignored) { }
        }
        return Optional.empty();
    }
}
