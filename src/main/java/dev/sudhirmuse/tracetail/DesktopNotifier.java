/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail;

import java.awt.AWTException;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;

final class DesktopNotifier {
    private static TrayIcon trayIcon;
    private DesktopNotifier() { }
    static synchronized void notify(String title, String message) {
        try {
            if (!SystemTray.isSupported()) { java.awt.Toolkit.getDefaultToolkit().beep(); return; }
            if (trayIcon == null) {
                trayIcon = new TrayIcon(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), "TraceTail");
                trayIcon.setImageAutoSize(true); SystemTray.getSystemTray().add(trayIcon);
            }
            trayIcon.displayMessage(title, message, TrayIcon.MessageType.WARNING);
        } catch (AWTException | RuntimeException exception) { java.awt.Toolkit.getDefaultToolkit().beep(); }
    }
}
