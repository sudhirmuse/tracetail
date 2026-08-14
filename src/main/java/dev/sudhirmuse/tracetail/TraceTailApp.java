/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail;

import dev.sudhirmuse.tracetail.core.SearchResultExporter;
import dev.sudhirmuse.tracetail.settings.AppPreferences;
import dev.sudhirmuse.tracetail.settings.PreferenceStore;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.SplitPane;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;

public final class TraceTailApp extends Application {
    private final TabPane tabs = new TabPane();
    private final Label status = new Label("Drop a log file here or press Ctrl+O");
    private final List<LogFileView> views = new ArrayList<>();
    private final PreferenceStore preferenceStore = PreferenceStore.userDefault();
    private final AppPreferences preferences = preferenceStore.load();
    private final Menu recentMenu = new Menu("Open Recent");
    private final ToggleGroup themeGroup = new ToggleGroup();
    private final java.util.Map<Path, Process> remoteProcesses = new java.util.HashMap<>();
    private Stage stage;
    private BorderPane root;
    private FileExplorerPane explorer;

    @Override public void start(Stage primaryStage) {
        LaunchOptions launchOptions;
        try { launchOptions = LaunchOptions.parse(getParameters().getRaw()); }
        catch (IllegalArgumentException exception) {
            new Alert(Alert.AlertType.ERROR, "Invalid command line: " + exception.getMessage(), ButtonType.OK).showAndWait();
            Platform.exit();
            return;
        }
        stage = primaryStage;
        Path scratch = preferences.scratchDirectory == null || preferences.scratchDirectory.isBlank()
            ? preferenceStore.path().getParent().resolve("scratch") : Path.of(preferences.scratchDirectory);
        explorer = new FileExplorerPane(stage, scratch, this::open, this::setStatus);
        SplitPane workspace = new SplitPane(explorer.node(), tabs);
        workspace.setDividerPositions(0.20);
        root = new BorderPane(workspace);
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
        applyTheme();
        installShortcuts(scene);
        stage.setTitle("TraceTail");
        stage.setScene(scene);
        stage.setMinWidth(820);
        stage.setMinHeight(520);
        if (launchOptions.position() != null) {
            stage.setX(launchOptions.position().left()); stage.setY(launchOptions.position().top());
            stage.setWidth(launchOptions.position().width()); stage.setHeight(launchOptions.position().height());
        }
        stage.setIconified(launchOptions.state() == LaunchOptions.WindowState.MINIMIZED);
        stage.setMaximized(launchOptions.state() == LaunchOptions.WindowState.MAXIMIZED);
        stage.setAlwaysOnTop(preferences.alwaysOnTop);
        applyTabSide();
        stage.setOnCloseRequest(event -> { views.forEach(LogFileView::close); remoteProcesses.values().forEach(Process::destroy); savePreferences(); });
        stage.show();

        if (!launchOptions.files().isEmpty()) launchOptions.files().forEach(this::open);
        else if (launchOptions.reopenRecent() && preferences.reopenRecentFiles)
            List.copyOf(preferences.recentFiles).stream().map(Path::of).filter(Files::isRegularFile).forEach(this::open);
    }

    private MenuBar menuBar() {
        MenuItem open = item("Open…", new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN), this::chooseFiles);
        MenuItem structuredOpen = item("Open Structured View…", null, this::chooseStructuredFile);
        MenuItem openRemote = item("Open Remote Log…", null, this::openRemote);
        MenuItem close = item("Close Tab", new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN), this::closeActive);
        MenuItem exit = item("Exit", null, Platform::exit);
        MenuItem export = item("Export Visible Results…", new KeyCodeCombination(KeyCode.E, KeyCombination.SHORTCUT_DOWN), this::exportResults);
        MenuItem copy = item("Copy Visible Results", new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN), () -> active().ifPresent(LogFileView::copyResults));
        refreshRecentMenu();
        Menu file = new Menu("File", null, open, structuredOpen, openRemote, recentMenu, close, new SeparatorMenuItem(), export, copy, new SeparatorMenuItem(), exit);

        MenuItem pause = item("Pause / Resume", new KeyCodeCombination(KeyCode.P, KeyCombination.SHORTCUT_DOWN), () -> active().ifPresent(LogFileView::togglePause));
        MenuItem follow = item("Follow Tail", new KeyCodeCombination(KeyCode.T, KeyCombination.SHORTCUT_DOWN), () -> active().ifPresent(LogFileView::toggleFollowTail));
        MenuItem clear = item("Clear", new KeyCodeCombination(KeyCode.L, KeyCombination.SHORTCUT_DOWN), () -> active().ifPresent(LogFileView::clear));
        MenuItem focus = item("Focus Filter", new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN), () -> active().ifPresent(LogFileView::focusFilter));
        MenuItem next = item("Find Next", new KeyCodeCombination(KeyCode.F3), () -> active().ifPresent(LogFileView::nextMatch));
        MenuItem previous = item("Find Previous", new KeyCodeCombination(KeyCode.F3, KeyCombination.SHIFT_DOWN), () -> active().ifPresent(LogFileView::previousMatch));
        MenuItem saveSearch = item("Save Current Search", new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN), () -> active().ifPresent(LogFileView::saveCurrentSearch));
        MenuItem deleteSearch = item("Delete Selected Saved Search", null, () -> active().ifPresent(LogFileView::deleteCurrentSavedSearch));
        MenuItem structuredView = item("Structured View / Format", null, () -> active().ifPresent(view -> showStructured(view.path())));
        Menu view = new Menu("View", null, themeMenu(), new SeparatorMenuItem(), follow, pause, clear, focus, next, previous,
            new SeparatorMenuItem(), structuredView, saveSearch, deleteSearch);

        MenuItem preferencesItem = item("Preferences…", null, this::showPreferences);
        MenuItem scratchFolder = item("Scratch Folder…", null, this::chooseScratchFolder);
        MenuItem exportPreferences = item("Export Preferences…", null, this::exportPreferences);
        MenuItem importPreferences = item("Import Preferences…", null, this::importPreferences);
        Menu preferencesMenu = new Menu("Preferences", null, preferencesItem, scratchFolder, exportPreferences, importPreferences);

        MenuItem timeline = item("Merged Timeline", null, () -> MergedTimelineDialog.show(stage, investigationEvents(), this::open, preferences.theme));
        MenuItem histogram = item("Error Histogram", null, () -> LogHistogramDialog.show(stage, investigationEvents(), this::open, preferences.theme));
        MenuItem journey = item("Thread / Trace Journey…", null, () -> JourneyDialog.show(stage, investigationEvents(), this::open, preferences.theme));
        MenuItem bookmarks = item("Bookmarks…", null, () -> BookmarksDialog.show(stage, preferences.bookmarks, this::open, preferences.theme));
        MenuItem compare = item("Compare Two Logs…", null, () -> CompareRunsDialog.show(stage, List.copyOf(views), preferences.theme));
        MenuItem compareFiles = item("Compare Structured Files…", null, () -> StructuredCompareDialog.chooseAndShow(stage, java.nio.charset.Charset.forName(preferences.charset), preferences.theme));
        MenuItem saveSession = item("Save Session…", null, this::saveSession);
        MenuItem loadSession = item("Load Session…", null, this::loadSession);
        MenuItem alerts = item("Live Alerts…", null, () -> { if (LiveAlertsDialog.show(preferences.alertRules)) savePreferences(); });
        MenuItem diagnostics = item("Developer Diagnostics…", null, () -> DeveloperToolsDialog.show(stage, diagnosticEvents(), preferenceStore.path().getParent(), preferences.theme, this::setStatus));
        Menu investigate = new Menu("Investigate", null, timeline, histogram, journey, compare, compareFiles, bookmarks, alerts, diagnostics, new SeparatorMenuItem(), saveSession, loadSession);

        MenuItem about = item("About TraceTail", null, () -> new Alert(Alert.AlertType.INFORMATION,
            "TraceTail 0.2.0\nAdvanced local log viewer and search tool\nApache-2.0", ButtonType.OK).showAndWait());
        return new MenuBar(file, view, investigate, preferencesMenu, new Menu("Help", null, about));
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
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.T, KeyCombination.SHORTCUT_DOWN), () -> active().ifPresent(LogFileView::toggleFollowTail));
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.L, KeyCombination.SHORTCUT_DOWN), () -> active().ifPresent(LogFileView::clear));
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN), () -> active().ifPresent(LogFileView::focusFilter));
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F3), () -> active().ifPresent(LogFileView::nextMatch));
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F3, KeyCombination.SHIFT_DOWN), () -> active().ifPresent(LogFileView::previousMatch));
    }

    private void chooseFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open log files");
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Log and text files", "*.log", "*.txt", "*.out", "*.json"),
            new FileChooser.ExtensionFilter("Structured files", "*.json", "*.xml", "*.yaml", "*.yml", "*.properties", "*.ini", "*.conf", "*.csv", "*.tsv", "*.sql", "*.md"),
            new FileChooser.ExtensionFilter("Compressed logs", "*.gz", "*.zip"),
            new FileChooser.ExtensionFilter("All files", "*.*"));
        List<java.io.File> selected = chooser.showOpenMultipleDialog(stage);
        if (selected != null) selected.forEach(file -> open(file.toPath()));
    }

    private void chooseStructuredFile() {
        FileChooser chooser = new FileChooser(); chooser.setTitle("Open Structured View");
        chooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Structured and text files", "*.json", "*.xml", "*.yaml", "*.yml", "*.properties", "*.ini", "*.conf", "*.csv", "*.tsv", "*.sql", "*.md", "*.txt", "*.log"),
            new FileChooser.ExtensionFilter("All files", "*.*"));
        java.io.File selected = chooser.showOpenDialog(stage); if (selected != null) showStructured(selected.toPath());
    }

    private void showStructured(Path path) {
        StructuredViewerDialog.show(stage, path.toAbsolutePath().normalize(), java.nio.charset.Charset.forName(preferences.charset), preferences.theme, this::setStatus);
    }

    private void openRemote() {
        RemoteSourceDialog.show(stage, preferences.theme).ifPresent(request -> {
            setStatus("Connecting to remote " + request.type().name().toLowerCase(java.util.Locale.ROOT) + " source…");
            Path directory = preferenceStore.path().getParent().resolve("remote");
            java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try { return new dev.sudhirmuse.tracetail.core.RemoteSourceService().open(request, directory); }
                catch (IOException exception) { throw new java.util.concurrent.CompletionException(exception); }
                catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new java.util.concurrent.CompletionException(exception);
                }
            }).whenComplete((opened, failure) -> Platform.runLater(() -> {
                if (failure != null) {
                    Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                    setStatus("Could not open remote log: " + cause.getMessage());
                    new Alert(Alert.AlertType.ERROR, "Could not open remote log:\n" + cause.getMessage(), ButtonType.OK).showAndWait();
                    return;
                }
                if (opened.process() != null) remoteProcesses.put(opened.spool().toAbsolutePath().normalize(), opened.process());
                open(opened.spool());
                setStatus("Remote log opened: " + opened.spool().getFileName());
            }));
        });
    }

    private void chooseScratchFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose TraceTail Scratch folder");
        Path current = explorer.scratchDirectory();
        if (Files.isDirectory(current)) chooser.setInitialDirectory(current.toFile());
        java.io.File selected = chooser.showDialog(stage);
        if (selected == null) return;
        explorer.setScratchDirectory(selected.toPath());
        preferences.scratchDirectory = explorer.scratchDirectory().toString();
        savePreferences();
    }

    private void open(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        String lowerName = normalized.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        if (lowerName.endsWith(".gz") || lowerName.endsWith(".zip")) {
            setStatus("Extracting " + normalized.getFileName() + " in the background…");
            java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try { return new dev.sudhirmuse.tracetail.core.CompressedLogExtractor().extract(normalized, preferenceStore.path().getParent().resolve("imports")); }
                catch (IOException exception) { throw new java.util.concurrent.CompletionException(exception); }
            }).whenComplete((files, failure) -> Platform.runLater(() -> {
                if (failure != null) setStatus("Could not extract " + normalized.getFileName() + ": " + failure.getCause().getMessage());
                else { files.forEach(this::open); setStatus("Opened " + files.size() + " compressed log file(s)"); }
            }));
            return;
        }
        for (LogFileView view : views) {
            if (view.path().equals(normalized)) { tabs.getSelectionModel().select(view.tab()); return; }
        }
        try {
            LogFileView view = new LogFileView(normalized, this::setStatus, preferences, this::savePreferences,
                this::refreshAllHighlighting, explorer::scratchDirectory, ignored -> explorer.refresh());
            views.add(view);
            Tab tab = view.tab();
            tab.setOnClosed(event -> { view.close(); views.remove(view); stopRemote(view.path()); });
            tabs.getTabs().add(tab);
            tabs.getSelectionModel().select(tab);
            view.start();
            remember(normalized);
            explorer.setWorkspaceIfUnset(normalized.getParent());
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
            stopRemote(view.path());
        });
    }
    private void stopRemote(Path path) {
        Process process = remoteProcesses.remove(path.toAbsolutePath().normalize());
        if (process != null) process.destroy();
    }
    private void setStatus(String message) { status.setText(message); }
    private void refreshAllHighlighting() { views.forEach(LogFileView::refreshHighlighting); }
    private List<InvestigationEvent> investigationEvents() {
        dev.sudhirmuse.tracetail.core.LogTimestampParser parser = new dev.sudhirmuse.tracetail.core.LogTimestampParser();
        return views.stream().flatMap(view -> view.investigationSnapshot().stream().map(event ->
                new InvestigationEvent(view.path(), parser.parse(event.content()).orElse(event.receivedAt()), event)))
            .sorted(Comparator.comparing(InvestigationEvent::timestamp)).limit(100_000).toList();
    }
    private List<dev.sudhirmuse.tracetail.core.DiagnosticEvent> diagnosticEvents() {
        return investigationEvents().stream().map(item -> new dev.sudhirmuse.tracetail.core.DiagnosticEvent(item.source(), item.timestamp(), item.event())).toList();
    }

    private void saveSession() {
        javafx.scene.control.TextInputDialog prompt = new javafx.scene.control.TextInputDialog("investigation-" + java.time.LocalDate.now());
        prompt.setTitle("Save Investigation Session"); prompt.setContentText("Session name:");
        prompt.showAndWait().map(String::strip).filter(value -> !value.isEmpty()).ifPresent(name -> {
            try {
                Path directory = preferenceStore.path().getParent().resolve("sessions"); Files.createDirectories(directory);
                String safe = name.replaceAll("[^A-Za-z0-9._-]", "_"); Path destination = directory.resolve(safe + ".json");
                InvestigationSession session = new InvestigationSession(name, java.time.Instant.now().toString(), views.stream().map(view -> view.path().toString()).toList());
                new com.fasterxml.jackson.databind.ObjectMapper().enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT).writeValue(destination.toFile(), session);
                setStatus("Saved session: " + destination.getFileName());
            } catch (IOException exception) { setStatus("Could not save session: " + exception.getMessage()); }
        });
    }

    private void loadSession() {
        FileChooser chooser = new FileChooser(); chooser.setTitle("Load Investigation Session");
        Path directory = preferenceStore.path().getParent().resolve("sessions"); if (Files.isDirectory(directory)) chooser.setInitialDirectory(directory.toFile());
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("TraceTail session", "*.json"));
        java.io.File selected = chooser.showOpenDialog(stage); if (selected == null) return;
        try {
            InvestigationSession session = new com.fasterxml.jackson.databind.ObjectMapper().readValue(selected, InvestigationSession.class);
            session.files().stream().map(Path::of).filter(Files::isRegularFile).forEach(this::open);
            setStatus("Loaded session: " + session.name());
        } catch (IOException exception) { setStatus("Could not load session: " + exception.getMessage()); }
    }

    private void remember(Path path) {
        String value = path.toString();
        preferences.recentFiles.removeIf(item -> item.equalsIgnoreCase(value));
        preferences.recentFiles.add(0, value);
        if (preferences.recentFiles.size() > 12) preferences.recentFiles.subList(12, preferences.recentFiles.size()).clear();
        refreshRecentMenu();
        savePreferences();
    }

    private void refreshRecentMenu() {
        recentMenu.getItems().clear();
        for (String recent : preferences.recentFiles) {
            MenuItem item = new MenuItem(recent);
            item.setOnAction(event -> open(Path.of(recent)));
            recentMenu.getItems().add(item);
        }
        recentMenu.setDisable(recentMenu.getItems().isEmpty());
    }

    private void savePreferences() {
        try { preferenceStore.save(preferences); }
        catch (IOException exception) { setStatus("Could not save preferences: " + exception.getMessage()); }
    }

    private void showPreferences() {
        if (!PreferencesDialog.show(preferences)) return;
        stage.setAlwaysOnTop(preferences.alwaysOnTop);
        applyTabSide();
        views.forEach(LogFileView::applyDisplayPreferences);
        applyTheme();
        savePreferences();
    }

    private void applyTabSide() {
        try { tabs.setSide(javafx.geometry.Side.valueOf(preferences.tabSide)); }
        catch (IllegalArgumentException exception) { tabs.setSide(javafx.geometry.Side.TOP); }
    }

    private void exportResults() {
        active().ifPresent(view -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Export visible results");
            chooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("CSV", "*.csv"),
                new FileChooser.ExtensionFilter("JSON", "*.json"), new FileChooser.ExtensionFilter("Text", "*.txt"));
            java.io.File destination = chooser.showSaveDialog(stage);
            if (destination == null) return;
            String name = destination.getName().toLowerCase();
            SearchResultExporter.Format format = name.endsWith(".json") ? SearchResultExporter.Format.JSON : name.endsWith(".csv") ? SearchResultExporter.Format.CSV : SearchResultExporter.Format.TEXT;
            try { view.exportResults(destination.toPath(), format); }
            catch (IOException exception) { setStatus("Could not export results: " + exception.getMessage()); }
        });
    }

    private void exportPreferences() {
        FileChooser chooser = new FileChooser(); chooser.setTitle("Export TraceTail preferences");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        java.io.File destination = chooser.showSaveDialog(stage);
        if (destination == null) return;
        try { preferenceStore.exportTo(destination.toPath(), preferences); setStatus("Exported preferences to " + destination); }
        catch (IOException exception) { setStatus("Could not export preferences: " + exception.getMessage()); }
    }

    private void importPreferences() {
        FileChooser chooser = new FileChooser(); chooser.setTitle("Import TraceTail preferences");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        java.io.File source = chooser.showOpenDialog(stage);
        if (source == null) return;
        try {
            AppPreferences imported = preferenceStore.importFrom(source.toPath());
            preferences.wrapLines = imported.wrapLines; preferences.alwaysOnTop = imported.alwaysOnTop;
            preferences.reopenRecentFiles = imported.reopenRecentFiles; preferences.fontSize = imported.fontSize;
            preferences.tabWidth = imported.tabWidth; preferences.charset = imported.charset; preferences.tabSide = imported.tabSide;
            preferences.theme = imported.theme;
            preferences.scratchDirectory = imported.scratchDirectory;
            preferences.savedSearches = imported.savedSearches; preferences.highlightRules = imported.highlightRules;
            preferences.bookmarks = imported.bookmarks; preferences.alertRules = imported.alertRules;
            showPreferencesApplied();
        } catch (IOException exception) { setStatus("Could not import preferences: " + exception.getMessage()); }
    }

    private void showPreferencesApplied() {
        stage.setAlwaysOnTop(preferences.alwaysOnTop); applyTabSide(); applyTheme(); views.forEach(LogFileView::applyDisplayPreferences); savePreferences();
        setStatus("Imported preferences");
    }

    private void applyTheme() {
        if (root == null) return;
        root.getStyleClass().removeAll("theme-dark", "theme-light");
        String selected = preferences.theme == null ? "DARK" : preferences.theme;
        for (Toggle toggle : themeGroup.getToggles()) {
            if (selected.equals(toggle.getUserData())) { themeGroup.selectToggle(toggle); break; }
        }
        boolean light = selected.equals("LIGHT") || (selected.equals("SYSTEM") && isSystemLight());
        root.getStyleClass().add(light ? "theme-light" : "theme-dark");
    }

    private Menu themeMenu() {
        Menu menu = new Menu("Theme");
        for (String value : List.of("DARK", "LIGHT", "SYSTEM")) {
            String label = value.charAt(0) + value.substring(1).toLowerCase();
            RadioMenuItem item = new RadioMenuItem(label);
            item.setUserData(value);
            item.setToggleGroup(themeGroup);
            item.setSelected(value.equals(preferences.theme));
            item.setOnAction(event -> {
                preferences.theme = value;
                applyTheme();
                savePreferences();
                setStatus(label + " theme applied");
            });
            menu.getItems().add(item);
        }
        return menu;
    }

    private boolean isSystemLight() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return false;
        try {
            Process process = new ProcessBuilder("reg", "query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                "/v", "AppsUseLightTheme").redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            return process.waitFor() == 0 && output.matches("(?s).*AppsUseLightTheme\\s+REG_DWORD\\s+0x1.*");
        } catch (IOException exception) { return false; }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); return false; }
    }
}
