/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail;

import dev.sudhirmuse.tracetail.core.StructuredDocumentService;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToolBar;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

final class StructuredViewerDialog {
    private StructuredViewerDialog() { }

    static void show(Stage owner, Path path, Charset charset, String theme, Consumer<String> status) {
        Stage stage = new Stage(); stage.initOwner(owner); stage.setTitle("Structured View — " + path.getFileName());
        BorderPane root = new BorderPane(new ProgressIndicator());
        Scene scene = new Scene(root, 1100, 760); ThemeSupport.apply(scene, theme); stage.setScene(scene); stage.show();
        CompletableFuture.supplyAsync(() -> {
            try { return new StructuredDocumentService().load(path, charset); }
            catch (IOException exception) { throw new java.util.concurrent.CompletionException(exception); }
        }).whenComplete((document, failure) -> Platform.runLater(() -> {
            if (failure != null) {
                Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                stage.close(); new Alert(Alert.AlertType.ERROR, cause.getMessage(), ButtonType.OK).showAndWait(); return;
            }
            root.setCenter(content(document));
            Button save = new Button("Save Formatted Copy…"); save.setDisable(document.raw().equals(document.formatted()));
            save.setOnAction(event -> save(owner, document, charset, status));
            root.setTop(new ToolBar(new Label("Detected: " + document.format()), save,
                new Label("Original file is read-only; formatting is shown in memory.")));
            status.accept("Opened " + path.getFileName() + " as " + document.format());
        }));
    }

    private static TabPane content(StructuredDocumentService.Document document) {
        Tab raw = tab("Raw", text(document.raw())); Tab formatted = tab("Formatted", text(document.formatted()));
        Tab tree = new Tab("Tree"); tree.setClosable(false);
        if (document.tree() == null) { tree.setDisable(true); tree.setContent(new Label("Tree view is available for JSON, XML, and YAML.")); }
        else tree.setContent(new TreeView<>(item(document.tree())));
        TabPane tabs = new TabPane(raw, formatted, tree); tabs.getSelectionModel().select(formatted); return tabs;
    }

    private static TextArea text(String value) { TextArea area = new TextArea(value); area.setEditable(false); area.setWrapText(false); area.getStyleClass().add("details"); return area; }
    private static Tab tab(String name, javafx.scene.Node content) { Tab tab = new Tab(name, content); tab.setClosable(false); return tab; }
    private static TreeItem<String> item(StructuredDocumentService.DocumentNode node) {
        TreeItem<String> item = new TreeItem<>(node.value().isBlank() ? node.name() : node.name() + " = " + node.value());
        node.children().stream().map(StructuredViewerDialog::item).forEach(item.getChildren()::add); return item;
    }

    private static void save(Stage owner, StructuredDocumentService.Document document, Charset charset, Consumer<String> status) {
        FileChooser chooser = new FileChooser(); chooser.setTitle("Save formatted copy");
        chooser.setInitialFileName(formattedName(document.path().getFileName().toString()));
        java.io.File selected = chooser.showSaveDialog(owner); if (selected == null) return;
        try { Files.writeString(selected.toPath(), document.formatted(), charset); status.accept("Saved formatted copy: " + selected); }
        catch (IOException exception) { new Alert(Alert.AlertType.ERROR, "Could not save formatted copy:\n" + exception.getMessage(), ButtonType.OK).showAndWait(); }
    }

    private static String formattedName(String name) { int dot = name.lastIndexOf('.'); return dot <= 0 ? name + "-formatted" : name.substring(0, dot) + "-formatted" + name.substring(dot); }
}
