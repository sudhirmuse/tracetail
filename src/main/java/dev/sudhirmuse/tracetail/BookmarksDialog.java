/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail;

import dev.sudhirmuse.tracetail.settings.AppPreferences;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseButton;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

final class BookmarksDialog {
    private BookmarksDialog() { }
    static void show(Stage owner, List<AppPreferences.Bookmark> bookmarks, Consumer<Path> openFile, String theme) {
        TableView<AppPreferences.Bookmark> table = new TableView<>(FXCollections.observableArrayList(bookmarks));
        table.getColumns().setAll(java.util.List.of(column("File", item -> Path.of(item.file()).getFileName().toString(), 200),
            column("Line", item -> Long.toString(item.lineNumber()), 80), column("Note", AppPreferences.Bookmark::note, 260),
            column("Preview", AppPreferences.Bookmark::preview, 500), column("Created", AppPreferences.Bookmark::createdAt, 180)));
        table.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null)
                openFile.accept(Path.of(table.getSelectionModel().getSelectedItem().file()));
        });
        Stage stage = new Stage(); stage.initOwner(owner); stage.setTitle("Investigation Bookmarks");
        Scene scene = new Scene(table, 1100, 600); ThemeSupport.apply(scene, theme); stage.setScene(scene); stage.show();
    }
    private static TableColumn<AppPreferences.Bookmark, String> column(String title, java.util.function.Function<AppPreferences.Bookmark, String> value, double width) {
        TableColumn<AppPreferences.Bookmark, String> column = new TableColumn<>(title);
        column.setCellValueFactory(item -> new ReadOnlyStringWrapper(value.apply(item.getValue()))); column.setPrefWidth(width); return column;
    }
}
