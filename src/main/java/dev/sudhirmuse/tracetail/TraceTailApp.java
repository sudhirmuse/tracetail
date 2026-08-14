/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class TraceTailApp extends Application {
    private final TabPane tabs = new TabPane();
    private final Label status = new Label("Drop a log file here or press Ctrl+O");
    private final List<LogFileView> views = new ArrayList<>();
    private Stage stage;

    @Override public void start(Stage primaryStage) {
        stage = primaryStage;
        BorderPane root = new BorderPane(tabs);
        root.setTop(menuBar());
        status.getStyleClass().add("status-bar");
        root.setBottom(status);
        root.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) event.acceptTransferModes(TransferMode.COPY);
            event.consume();
        });
        root.setOnDragDropped(event -> {
            Dragboard board = event.getDragboard();
            if (board.hasFiles()) board.getFiles().forEach(file -> open(file.toPath()));
            event.setDropCompleted(board.hasFiles());
            event.consume();
        });

        Scene scene = new Scene(root, 1240, 780);
        scene.getStylesheets().add(getClass().getResource("tracetail.css").toExternalForm());
        installShortcuts(scene);
        stage.setTitle("TraceTail");
        stage.setScene(scene);
        stage.setMinWidth(820);
        stage.setMinHeight(520);
        stage.setOnCloseRequest(event -> views.forEach(LogFileView::close));
        stage.show();

        getParameters().getRaw().stream().filter(value -> !value.startsWith("-")).map(Path::of).forEach(this::open);
    }

    private MenuBar menuBar() {
        MenuItem open = item("Open…", new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN), this::chooseFiles);
        MenuItem close = item("Close Tab", new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN), this::closeActive);
        MenuItem exit = item("Exit", null, Platform::exit);
        Menu file = new Menu("File", null, open, close, exit);

        MenuItem pause = item("Pause / Resume", new KeyCodeCombination(KeyCode.P, KeyCombination.SHORTCUT_DOWN), () -> active().ifPresent(LogFileView::togglePause));
        MenuItem clear = item("Clear", new KeyCodeCombination(KeyCode.L, KeyCombination.SHORTCUT_DOWN), () -> active().ifPresent(LogFileView::clear));
        MenuItem focus = item("Focus Filter", new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN), () -> active().ifPresent(LogFileView::focusFilter));
        Menu view = new Menu("View", null, pause, clear, focus);

        MenuItem about = item("About TraceTail", null, () -> new Alert(Alert.AlertType.INFORMATION,
            "TraceTail 0.1.0\nLocal Java and Spring log viewer\nApache-2.0", ButtonType.OK).showAndWait());
        return new MenuBar(file, view, new Menu("Help", null, about));
    }

    private MenuItem item(String text, KeyCombination accelerator, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setAccelerator(accelerator);
        item.setOnAction(event -> action.run());
        return item;
    }

    private void installShortcuts(Scene scene) {
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN), this::chooseFiles);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN), this::closeActive);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.P, KeyCombination.SHORTCUT_DOWN), () -> active().ifPresent(LogFileView::togglePause));
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.L, KeyCombination.SHORTCUT_DOWN), () -> active().ifPresent(LogFileView::clear));
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN), () -> active().ifPresent(LogFileView::focusFilter));
    }

    private void chooseFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open log files");
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Log and text files", "*.log", "*.txt", "*.out", "*.json"),
            new FileChooser.ExtensionFilter("All files", "*.*"));
        List<java.io.File> selected = chooser.showOpenMultipleDialog(stage);
        if (selected != null) selected.forEach(file -> open(file.toPath()));
    }

    private void open(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        for (LogFileView view : views) {
            if (view.path().equals(normalized)) { tabs.getSelectionModel().select(view.tab()); return; }
        }
        try {
            LogFileView view = new LogFileView(normalized, this::setStatus);
            views.add(view);
            Tab tab = view.tab();
            tab.setOnClosed(event -> { view.close(); views.remove(view); });
            tabs.getTabs().add(tab);
            tabs.getSelectionModel().select(tab);
            view.start();
        } catch (IOException exception) {
            setStatus("Could not open " + normalized + ": " + exception.getMessage());
            new Alert(Alert.AlertType.ERROR, "Could not open log file:\n" + exception.getMessage(), ButtonType.OK).showAndWait();
        }
    }

    private java.util.Optional<LogFileView> active() {
        Tab selected = tabs.getSelectionModel().getSelectedItem();
        return views.stream().filter(view -> view.tab() == selected).findFirst();
    }

    private void closeActive() {
        active().ifPresent(view -> {
            view.close();
            views.remove(view);
            tabs.getTabs().remove(view.tab());
        });
    }
    private void setStatus(String message) { status.setText(message); }
}
