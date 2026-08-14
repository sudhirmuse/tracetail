/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

record LaunchOptions(List<Path> files, WindowPosition position, WindowState state, boolean reopenRecent) {
    enum WindowState { NORMAL, MINIMIZED, MAXIMIZED }
    record WindowPosition(double left, double top, double width, double height) { }

    static LaunchOptions parse(List<String> arguments) {
        List<Path> files = new ArrayList<>();
        WindowPosition position = null;
        WindowState state = WindowState.NORMAL;
        boolean reopen = true;
        for (int index = 0; index < arguments.size(); index++) {
            String argument = arguments.get(index);
            switch (argument) {
                case "--window-position", "-wp" -> {
                    if (index + 4 >= arguments.size()) throw new IllegalArgumentException(argument + " requires left top width height");
                    position = new WindowPosition(Double.parseDouble(arguments.get(++index)), Double.parseDouble(arguments.get(++index)),
                        Double.parseDouble(arguments.get(++index)), Double.parseDouble(arguments.get(++index)));
                }
                case "--window-state", "-ws" -> {
                    if (++index >= arguments.size()) throw new IllegalArgumentException(argument + " requires normal, minimized, or maximized");
                    state = switch (arguments.get(index).toLowerCase()) {
                        case "0", "normal" -> WindowState.NORMAL;
                        case "1", "minimized" -> WindowState.MINIMIZED;
                        case "2", "maximized" -> WindowState.MAXIMIZED;
                        default -> throw new IllegalArgumentException("Unknown window state: " + arguments.get(index));
                    };
                }
                case "--no-reopen" -> reopen = false;
                default -> {
                    if (argument.startsWith("-")) throw new IllegalArgumentException("Unknown option: " + argument);
                    files.add(Path.of(argument));
                }
            }
        }
        return new LaunchOptions(List.copyOf(files), position, state, reopen);
    }
}
