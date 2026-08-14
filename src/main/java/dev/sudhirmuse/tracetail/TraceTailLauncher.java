/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail;

import javafx.application.Application;

import java.util.List;

public final class TraceTailLauncher {
    private TraceTailLauncher() {}

    public static void main(String[] args) {
        if (List.of(args).contains("--version")) {
            System.out.println("TraceTail 0.1.0");
            return;
        }
        if (List.of(args).contains("--help") || List.of(args).contains("-h")) {
            System.out.println("Usage: tracetail [log-file ...]\nOpen Java and Spring log files in a local desktop viewer.");
            return;
        }
        Application.launch(TraceTailApp.class, args);
    }
}
