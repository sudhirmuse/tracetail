/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail;

import javafx.scene.Scene;

final class ThemeSupport {
    private ThemeSupport() { }
    static void apply(Scene scene, String theme) {
        scene.getStylesheets().add(TraceTailApp.class.getResource("tracetail.css").toExternalForm());
        String selected = theme == null ? "DARK" : theme;
        boolean light = selected.equals("LIGHT") || (selected.equals("SYSTEM") && systemLight());
        scene.getRoot().getStyleClass().add(light ? "theme-light" : "theme-dark");
    }
    private static boolean systemLight() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return false;
        try {
            Process process = new ProcessBuilder("reg", "query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize", "/v", "AppsUseLightTheme").redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            return process.waitFor() == 0 && output.matches("(?s).*AppsUseLightTheme\\s+REG_DWORD\\s+0x1.*");
        } catch (java.io.IOException exception) { return false; }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); return false; }
    }
}
