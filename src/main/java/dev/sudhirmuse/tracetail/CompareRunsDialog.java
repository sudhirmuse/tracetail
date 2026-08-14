/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail;

import dev.sudhirmuse.tracetail.core.LogRunComparator;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import java.util.List;

final class CompareRunsDialog {
    private CompareRunsDialog() { }
    static void show(Stage owner, List<LogFileView> views, String theme) {
        if (views.size() < 2) { new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION, "Open at least two files to compare.").showAndWait(); return; }
        ChoiceDialog<LogFileView> left = choice("Choose baseline log", views); var a = left.showAndWait(); if (a.isEmpty()) return;
        ChoiceDialog<LogFileView> right = choice("Choose comparison log", views.stream().filter(view -> view != a.get()).toList()); var b = right.showAndWait(); if (b.isEmpty()) return;
        List<LogRunComparator.Delta> deltas = new LogRunComparator().compare(a.get().investigationSnapshot(), b.get().investigationSnapshot());
        TableView<LogRunComparator.Delta> table = new TableView<>(FXCollections.observableArrayList(deltas));
        table.getColumns().setAll(java.util.List.of(column("Pattern", LogRunComparator.Delta::pattern, 700), column("Baseline", value -> Long.toString(value.leftCount()), 100),
            column("Comparison", value -> Long.toString(value.rightCount()), 100), column("Change", value -> Long.toString(value.difference()), 100)));
        Stage stage = new Stage(); stage.initOwner(owner); stage.setTitle("Compare Logs — " + a.get().path().getFileName() + " vs " + b.get().path().getFileName());
        Scene scene = new Scene(table, 1050, 650); ThemeSupport.apply(scene, theme); stage.setScene(scene); stage.show();
    }
    private static ChoiceDialog<LogFileView> choice(String title, List<LogFileView> views) {
        ChoiceDialog<LogFileView> dialog = new ChoiceDialog<>(views.getFirst(), views); dialog.setTitle("Compare Logs"); dialog.setHeaderText(title);
        return dialog;
    }
    private static TableColumn<LogRunComparator.Delta, String> column(String title, java.util.function.Function<LogRunComparator.Delta, String> value, double width) {
        TableColumn<LogRunComparator.Delta, String> column = new TableColumn<>(title); column.setCellValueFactory(item -> new ReadOnlyStringWrapper(value.apply(item.getValue()))); column.setPrefWidth(width); return column;
    }
}
