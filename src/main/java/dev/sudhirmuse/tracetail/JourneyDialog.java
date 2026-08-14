/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail;

import dev.sudhirmuse.tracetail.core.LogLevel;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

final class JourneyDialog {
    private JourneyDialog() { }
    static void show(Stage owner, List<InvestigationEvent> all, Consumer<Path> openFile, String theme) {
        TextInputDialog prompt = new TextInputDialog(); prompt.initOwner(owner); prompt.setTitle("Thread / Trace Journey");
        prompt.setHeaderText("Follow an operation across every open log"); prompt.setContentText("Thread or trace ID:");
        prompt.showAndWait().map(String::strip).filter(value -> !value.isEmpty()).ifPresent(id -> {
            List<InvestigationEvent> matches = all.stream().filter(item -> item.event().threadId().equalsIgnoreCase(id)
                || item.event().traceId().equalsIgnoreCase(id) || item.event().content().contains(id)).toList();
            if (matches.isEmpty()) { new Alert(Alert.AlertType.INFORMATION, "No events found for " + id, ButtonType.OK).showAndWait(); return; }
            long errors = matches.stream().filter(item -> item.event().level() == LogLevel.ERROR || item.event().level() == LogLevel.FATAL).count();
            long warnings = matches.stream().filter(item -> item.event().level() == LogLevel.WARN).count();
            Duration duration = Duration.between(matches.getFirst().timestamp(), matches.getLast().timestamp()).abs();
            MergedTimelineDialog.show(owner, matches, openFile, theme);
            new Alert(Alert.AlertType.INFORMATION, "Journey: " + id + "\nEvents: " + matches.size() + "\nFiles: "
                + matches.stream().map(InvestigationEvent::source).distinct().count() + "\nDuration: " + duration.toMillis()
                + " ms\nWarnings: " + warnings + "\nErrors: " + errors, ButtonType.OK).showAndWait();
        });
    }
}
