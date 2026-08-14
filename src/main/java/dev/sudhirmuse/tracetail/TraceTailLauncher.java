/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail;

import javafx.application.Application;

import java.util.List;

public final class TraceTailLauncher {
    private TraceTailLauncher() {}

    public static void main(String[] args) {
        if (List.of(args).contains("--version")) {
            System.out.println("TraceTail 0.2.0");
            return;
        }
        if (List.of(args).contains("--help") || List.of(args).contains("-h")) {
            System.out.println("""
                Usage: tracetail [options] [log-file ...]
                Options:
                  --window-position, -wp LEFT TOP WIDTH HEIGHT
                  --window-state, -ws normal|minimized|maximized
                  --no-reopen     Do not reopen recent files
                  --version       Print the version
                  --help, -h      Show this help
                """);
            return;
        }
        Application.launch(TraceTailApp.class, args);
    }
}
