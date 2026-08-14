/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

final class FileExplorerPane {
    private static final int OPEN_ALL_LIMIT = 200;
    private final Stage owner;
    private Path scratch;
    private final Consumer<Path> openFile;
    private final Consumer<String> status;
    private final TreeView<Path> tree = new TreeView<>();
    private final BorderPane pane = new BorderPane();
    private Path workspace;

    FileExplorerPane(Stage owner, Path scratch, Consumer<Path> openFile, Consumer<String> status) {
        this.owner = owner;
        this.scratch = scratch.toAbsolutePath().normalize();
        this.openFile = openFile;
        this.status = status;
        try { Files.createDirectories(this.scratch); }
        catch (IOException exception) { status.accept("Could not create Scratch folder: " + exception.getMessage()); }
        configureTree();
        Button folder = new Button("Folder…"); folder.setOnAction(event -> chooseFolder());
        Button openAll = new Button("Open All"); openAll.setOnAction(event -> openAllSelected());
        Button refresh = new Button("Refresh"); refresh.setOnAction(event -> refresh());
        pane.setTop(new ToolBar(folder, openAll, refresh));
        pane.setCenter(tree);
        pane.setMinWidth(210);
        pane.setPrefWidth(280);
        refresh();
    }

    BorderPane node() { return pane; }
    Path scratchDirectory() { return scratch; }

    void setScratchDirectory(Path directory) {
        Path selected = directory.toAbsolutePath().normalize();
        try {
            Files.createDirectories(selected);
            scratch = selected;
            refresh();
            status.accept("Scratch folder: " + scratch);
        } catch (IOException exception) {
            status.accept("Could not use Scratch folder: " + exception.getMessage());
        }
    }

    void setWorkspaceIfUnset(Path folder) {
        if (workspace == null && folder != null && Files.isDirectory(folder)) { workspace = folder.toAbsolutePath().normalize(); refresh(); }
    }

    void refresh() {
        TreeItem<Path> root = new TreeItem<>();
        root.setExpanded(true);
        if (workspace != null) root.getChildren().add(new PathItem(workspace));
        root.getChildren().add(new PathItem(scratch));
        tree.setRoot(root);
        tree.setShowRoot(false);
    }

    private void configureTree() {
        tree.setCellFactory(ignored -> new TreeCell<>() {
            @Override protected void updateItem(Path path, boolean empty) {
                super.updateItem(path, empty);
                if (empty || path == null) { setText(null); setTooltip(null); return; }
                Path name = path.getFileName();
                setText(path.equals(scratch) ? "Scratch" : name == null ? path.toString() : name.toString());
                setTooltip(new Tooltip(path.toString()));
            }
        });
        tree.setOnMouseClicked(event -> {
            if (event.getButton() != MouseButton.PRIMARY || event.getClickCount() != 2) return;
            TreeItem<Path> selected = tree.getSelectionModel().getSelectedItem();
            if (selected != null && selected.getValue() != null && Files.isRegularFile(selected.getValue())) openFile.accept(selected.getValue());
        });
    }

    private void chooseFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose TraceTail workspace folder");
        if (workspace != null && Files.isDirectory(workspace)) chooser.setInitialDirectory(workspace.toFile());
        java.io.File selected = chooser.showDialog(owner);
        if (selected != null) { workspace = selected.toPath().toAbsolutePath().normalize(); refresh(); status.accept("Workspace: " + workspace); }
    }

    private void openAllSelected() {
        TreeItem<Path> selected = tree.getSelectionModel().getSelectedItem();
        Path folder = selected == null ? workspace : selected.getValue();
        if (folder != null && Files.isRegularFile(folder)) folder = folder.getParent();
        if (folder == null || !Files.isDirectory(folder)) { status.accept("Select a folder in the explorer first"); return; }
        try (var paths = Files.list(folder)) {
            List<Path> files = paths.filter(Files::isRegularFile).filter(this::supported).sorted().limit(OPEN_ALL_LIMIT + 1L).toList();
            if (files.size() > OPEN_ALL_LIMIT) { status.accept("Folder has more than " + OPEN_ALL_LIMIT + " supported files; refine the folder first"); return; }
            files.forEach(openFile);
            status.accept("Opened " + files.size() + " files from " + folder);
        } catch (IOException exception) { status.accept("Could not open folder: " + exception.getMessage()); }
    }

    private boolean supported(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".log") || name.endsWith(".txt") || name.endsWith(".out") || name.endsWith(".json")
            || name.endsWith(".xml") || name.endsWith(".yaml") || name.endsWith(".yml") || name.endsWith(".properties")
            || name.endsWith(".ini") || name.endsWith(".conf") || name.endsWith(".csv") || name.endsWith(".tsv")
            || name.endsWith(".sql") || name.endsWith(".md") || name.endsWith(".trace") || name.endsWith(".gz") || name.endsWith(".zip");
    }

    private static final class PathItem extends TreeItem<Path> {
        private boolean loaded;
        PathItem(Path path) { super(path); }
        @Override public boolean isLeaf() { return Files.isRegularFile(getValue()); }
        @Override public javafx.collections.ObservableList<TreeItem<Path>> getChildren() {
            if (!loaded) {
                loaded = true;
                if (Files.isDirectory(getValue())) {
                    try (var entries = Files.list(getValue())) {
                        entries.sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                            .map(PathItem::new).forEach(super.getChildren()::add);
                    } catch (IOException ignored) { /* inaccessible folders remain empty */ }
                }
            }
            return super.getChildren();
        }
    }
}
