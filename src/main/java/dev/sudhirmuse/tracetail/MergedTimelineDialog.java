/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseButton;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

final class MergedTimelineDialog {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());
    private MergedTimelineDialog() { }

    static void show(Stage owner, List<InvestigationEvent> events, Consumer<Path> openFile, String theme) {
        TableView<InvestigationEvent> table = new TableView<>(FXCollections.observableArrayList(events));
        table.getColumns().setAll(java.util.List.of(column("Time", item -> TIME.format(item.timestamp()), 180),
            column("Source", item -> item.source().getFileName().toString(), 180),
            column("Level", item -> item.event().level().toString(), 80),
            column("Thread", item -> item.event().threadId(), 180),
            column("Message", item -> item.event().summary(), 700)));
        table.setPlaceholder(new Label("Open log files to build a merged timeline."));
        table.setRowFactory(ignored -> new TableRow<>() {
            @Override protected void updateItem(InvestigationEvent item, boolean empty) {
                super.updateItem(item, empty); setStyle("");
                if (!empty && item != null) {
                    int hue = Math.floorMod(item.source().toString().hashCode(), 360);
                    setStyle("-fx-border-color: transparent transparent transparent hsb(" + hue + ",65%,65%); -fx-border-width: 0 0 0 4;");
                }
            }
        });
        table.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null)
                openFile.accept(table.getSelectionModel().getSelectedItem().source());
        });
        Stage stage = new Stage(); stage.initOwner(owner); stage.setTitle("Merged Log Timeline — " + events.size() + " events");
        Scene scene = new Scene(table, 1200, 720); ThemeSupport.apply(scene, theme); stage.setScene(scene); stage.show();
    }

    private static TableColumn<InvestigationEvent, String> column(String title, java.util.function.Function<InvestigationEvent, String> value, double width) {
        TableColumn<InvestigationEvent, String> column = new TableColumn<>(title);
        column.setCellValueFactory(item -> new ReadOnlyStringWrapper(value.apply(item.getValue()))); column.setPrefWidth(width); return column;
    }
}
