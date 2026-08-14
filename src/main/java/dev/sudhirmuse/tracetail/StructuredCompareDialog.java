/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail;

import dev.sudhirmuse.tracetail.core.StructuredCompareService;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

final class StructuredCompareDialog {
    private StructuredCompareDialog() { }

    static void chooseAndShow(Stage owner, Charset charset, String theme) {
        Path left = choose(owner, "Choose left / baseline file"); if (left == null) return;
        Path right = choose(owner, "Choose right / comparison file"); if (right == null) return;
        show(owner, left, right, charset, theme);
    }

    private static void show(Stage owner, Path left, Path right, Charset charset, String theme) {
        Stage stage = new Stage(); stage.initOwner(owner); stage.setTitle("Compare Files — " + left.getFileName() + " vs " + right.getFileName());
        BorderPane root = new BorderPane(); Label summary = new Label("Ready"); CheckBox semantic = new CheckBox("Semantic JSON/XML/YAML"); semantic.setSelected(true);
        CheckBox whitespace = new CheckBox("Ignore whitespace"); whitespace.setSelected(true); Button compare = new Button("Compare");
        root.setTop(new ToolBar(semantic, whitespace, compare, summary));
        Scene scene = new Scene(root, 1250, 780); ThemeSupport.apply(scene, theme); stage.setScene(scene); stage.show();
        Runnable execute = () -> { compare.setDisable(true); summary.setText("Comparing…");
            CompletableFuture.supplyAsync(() -> { try { return new StructuredCompareService().compare(left, right, charset, semantic.isSelected(), whitespace.isSelected()); }
                catch (java.io.IOException exception) { throw new java.util.concurrent.CompletionException(exception); } })
                .whenComplete((result, failure) -> Platform.runLater(() -> { compare.setDisable(false);
                    if (failure != null) { Throwable cause = failure.getCause() == null ? failure : failure.getCause(); summary.setText("Comparison failed"); new Alert(Alert.AlertType.ERROR, cause.getMessage(), ButtonType.OK).showAndWait(); return; }
                    summary.setText(result.summary()); root.setCenter(content(result)); })); };
        compare.setOnAction(event -> execute.run()); execute.run();
    }

    private static TabPane content(StructuredCompareService.Result result) {
        TableView<StructuredCompareService.Row> table = new TableView<>(FXCollections.observableArrayList(result.rows()));
        table.getColumns().setAll(java.util.List.of(column("L#", row -> number(row.leftLine()), 55), column("Left", StructuredCompareService.Row::left, 535),
            column("R#", row -> number(row.rightLine()), 55), column("Right", StructuredCompareService.Row::right, 535)));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setRowFactory(ignored -> new TableRow<>() { @Override protected void updateItem(StructuredCompareService.Row row, boolean empty) { super.updateItem(row, empty); setStyle(empty || row == null ? "" : switch (row.kind()) {
            case ADDED -> "-fx-background-color: rgba(35, 134, 54, 0.28);"; case REMOVED -> "-fx-background-color: rgba(218, 54, 51, 0.28);";
            case CHANGED -> "-fx-background-color: rgba(210, 153, 34, 0.28);"; case SAME -> ""; }); }});
        TextArea unified = new TextArea(result.unified()); unified.setEditable(false); unified.setWrapText(false); unified.getStyleClass().add("details");
        Tab side = new Tab("Side by Side", table); side.setClosable(false); Tab text = new Tab("Unified", unified); text.setClosable(false); return new TabPane(side, text);
    }

    private static TableColumn<StructuredCompareService.Row, String> column(String name, java.util.function.Function<StructuredCompareService.Row, String> value, double width) {
        TableColumn<StructuredCompareService.Row, String> column = new TableColumn<>(name); column.setCellValueFactory(item -> new ReadOnlyStringWrapper(value.apply(item.getValue()))); column.setPrefWidth(width); return column;
    }
    private static String number(Integer value) { return value == null ? "" : value.toString(); }
    private static Path choose(Stage owner, String title) { FileChooser chooser = new FileChooser(); chooser.setTitle(title); chooser.getExtensionFilters().addAll(
        new FileChooser.ExtensionFilter("Structured and text files", "*.json", "*.xml", "*.yaml", "*.yml", "*.properties", "*.ini", "*.conf", "*.csv", "*.tsv", "*.sql", "*.md", "*.txt", "*.log"),
        new FileChooser.ExtensionFilter("All files", "*.*")); java.io.File selected = chooser.showOpenDialog(owner); return selected == null ? null : selected.toPath(); }
}
