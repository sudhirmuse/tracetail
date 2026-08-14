/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import java.util.regex.Pattern;

public final class LogRedactor {
    private static final Pattern WINDOWS_HOME = Pattern.compile("(?i)(?:[A-Z]:\\\\+Users\\\\+)[^\\\\\\s]+(?=\\\\+)");
    private static final Pattern UNIX_HOME = Pattern.compile("/(?:home|Users)/[^/\\s]+(?=/)");
    private static final Pattern BEARER = Pattern.compile("(?i)(Bearer\\s+)[A-Za-z0-9._~+/=-]+");
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
        "(?i)(password|passwd|authorization|cookie|api[_-]?key|secret|access[_-]?token|refresh[_-]?token|token)(\\s*[=:]\\s*)([^\\s,;&]+)");
    private static final Pattern JSON_SECRET = Pattern.compile(
        "(?i)([\"'])(password|passwd|authorization|cookie|api[_-]?key|secret|access[_-]?token|refresh[_-]?token|token)\\1(\\s*:\\s*)([\"'])(.*?)(\\4)");
    private static final Pattern JWT = Pattern.compile("\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}(?:\\.[A-Za-z0-9_-]+)?\\b");
    private static final Pattern EMAIL = Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern PAYMENT_CARD = Pattern.compile("(?<!\\d)(?:\\d[ -]?){13,19}(?!\\d)");

    public String redact(String input) {
        if (input == null || input.isEmpty()) return "";
        String value = WINDOWS_HOME.matcher(input).replaceAll("C:\\\\Users\\\\<user>");
        value = UNIX_HOME.matcher(value).replaceAll("/home/<user>");
        value = BEARER.matcher(value).replaceAll("$1<redacted>");
        value = JWT.matcher(value).replaceAll("<jwt-redacted>");
        value = EMAIL.matcher(value).replaceAll("<email-redacted>");
        value = PAYMENT_CARD.matcher(value).replaceAll("<payment-card-redacted>");
        value = JSON_SECRET.matcher(value).replaceAll("$1$2$1$3$4<redacted>$6");
        return SECRET_ASSIGNMENT.matcher(value).replaceAll("$1$2<redacted>");
    }

    public boolean containsSensitive(String input) { return !redact(input).equals(input == null ? "" : input); }
}
