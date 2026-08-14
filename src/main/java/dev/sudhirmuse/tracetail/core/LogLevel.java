/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

public enum LogLevel {
    TRACE, DEBUG, INFO, WARN, ERROR, FATAL, UNKNOWN;

    public boolean atLeast(LogLevel minimum) {
        return rank(this) >= rank(minimum);
    }

    private static int rank(LogLevel level) {
        return switch (level) {
            case TRACE -> 0;
            case DEBUG -> 1;
            case INFO -> 2;
            case WARN -> 3;
            case ERROR -> 4;
            case FATAL -> 5;
            case UNKNOWN -> 0;
        };
    }
}
