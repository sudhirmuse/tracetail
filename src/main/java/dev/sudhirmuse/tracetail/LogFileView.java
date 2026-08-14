/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.sudhirmuse.tracetail.core.BoundedEventBuffer;
import dev.sudhirmuse.tracetail.core.ContextSelector;
import dev.sudhirmuse.tracetail.core.EventFilter;
import dev.sudhirmuse.tracetail.core.FileTailer;
import dev.sudhirmuse.tracetail.core.FileWindowReader;
import dev.sudhirmuse.tracetail.core.LogEvent;
import dev.sudhirmuse.tracetail.core.LogEventParser;
import dev.sudhirmuse.tracetail.core.LogLevel;
import dev.sudhirmuse.tracetail.core.LargeFileSearcher;
import dev.sudhirmuse.tracetail.core.PagedLineReader;
import dev.sudhirmuse.tracetail.core.SearchPattern;
import dev.sudhirmuse.tracetail.core.SearchResultExporter;
import dev.sudhirmuse.tracetail.core.SparseLineIndex;
import dev.sudhirmuse.tracetail.core.StructuredFieldExtractor;
import dev.sudhirmuse.tracetail.settings.AppPreferences;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

final class LogFileView implements AutoCloseable {
    private static final int CAPACITY = 20_000;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());
    private final Path path;
    private final Consumer<String> status;
    private final AppPreferences preferences;
    private final Runnable savePreferences;
    private final Runnable refreshAllHighlighting;
    private final java.util.function.Supplier<Path> scratchDirectory;
    private final Consumer<Path> scratchCreated;
    private final Tab tab;
    private final ObservableList<LogEvent> displayed = FXCollections.observableArrayList();
    private final FilteredList<LogEvent> filtered = new FilteredList<>(displayed);
    private final BoundedEventBuffer buffer = new BoundedEventBuffer(CAPACITY);
    private final LogEventParser parser = new LogEventParser();
    private final TableView<LogEvent> table = new TableView<>(filtered);
    private final TextArea details = new TextArea();
    private final TextField query = new TextField();
    private final TextField trace = new TextField();
    private final TextField thread = new TextField();
    private final TextField structured = new TextField();
    private final CheckBox regex = new CheckBox("Regex");
    private final CheckBox onlyMatches = new CheckBox("Only matches");
    private final CheckBox followTail = new CheckBox("Follow Tail");
    private final CheckBox analyze = new CheckBox("Analyze");
    private final Hyperlink highlightingLink = new Hyperlink("Highlighting…");
    private final ComboBox<LogLevel> level = new ComboBox<>();
    private final ComboBox<FilterMode> filterMode = new ComboBox<>();
    private final ComboBox<ContextSelector.Mode> scratchMode = new ComboBox<>();
    private final ComboBox<AppPreferences.SavedSearch> savedSearches = new ComboBox<>();
    private final Button pauseButton = new Button("Pause");
    private final Button previousButton = new Button("Previous");
    private final Button nextButton = new Button("Next");
    private final Button historyButton = new Button("History…");
    private final Button searchFileButton = new Button("Search File");
    private final Button scratchButton = new Button("Save Search to Scratch");
    private final Button bookmarkButton = new Button("Bookmark");
    private final Label count = new Label("0 events");
    private final Label matchCount = new Label("No search");
    private final Label indexStatus = new Label("Index on demand");
    private final PauseTransition idleFlush = new PauseTransition(Duration.millis(400));
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final StructuredFieldExtractor fieldExtractor = new StructuredFieldExtractor();
    private final FileTailer tailer;
    private final SparseLineIndex lineIndex;
    private final PagedLineReader pagedReader;
    private final ExecutorService pageWorker;
    private boolean paused;
    private boolean unread;
    private boolean tailError;
    private SearchPattern searchPattern = SearchPattern.compile("", false);
    private AtomicBoolean searchCancellation;
    private boolean indexStarted;
    private boolean pendingWholeFileSearch;
    private long fastSequence;
    private LogEvent lastInspected;
    private final java.util.Map<String, Long> lastAlertAt = new java.util.HashMap<>();
    private long lineNumber;

    private enum FilterMode { SHOW_ALL, INCLUDE_MATCHES, EXCLUDE_MATCHES;
        @Override public String toString() { return switch (this) {
            case SHOW_ALL -> "Highlight matches";
            case INCLUDE_MATCHES -> "Show matches only";
            case EXCLUDE_MATCHES -> "Hide matches";
        }; }
    }

    LogFileView(Path path, Consumer<String> status, AppPreferences preferences, Runnable savePreferences,
                Runnable refreshAllHighlighting, java.util.function.Supplier<Path> scratchDirectory, Consumer<Path> scratchCreated) throws IOException {
        this.path = path;
        this.status = status;
        this.preferences = preferences;
        this.savePreferences = savePreferences;
        this.refreshAllHighlighting = refreshAllHighlighting;
        this.scratchDirectory = scratchDirectory;
        this.scratchCreated = scratchCreated;
        this.tab = new Tab(path.getFileName().toString());
        this.tab.setTooltip(new javafx.scene.control.Tooltip(path.toString()));
        this.tab.setClosable(true);
        this.tailer = new FileTailer(path, lines -> Platform.runLater(() -> accept(lines)),
            exception -> Platform.runLater(() -> { tailError = true; updateTabTitle(); status.accept("Tail error for " + path.getFileName() + ": " + exception.getMessage()); }),
            Charset.forName(preferences.charset));
        Charset charset = Charset.forName(preferences.charset);
        this.lineIndex = new SparseLineIndex(path, charset, snapshot -> Platform.runLater(() -> updateIndexStatus(snapshot)));
        this.pagedReader = new PagedLineReader(path, charset, lineIndex, 5_000);
        this.pageWorker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "tracetail-pages-" + path.getFileName());
            thread.setDaemon(true);
            return thread;
        });
        tab.selectedProperty().addListener((observable, old, selected) -> { if (selected) { unread = false; updateTabTitle(); } });
        configureTable();
        configureFilters();
        configureDetails();
        tab.setContent(layout());
        idleFlush.setOnFinished(event -> append(parser.finish()));
    }

    Path path() { return path; }
    @Override public String toString() { return path.getFileName().toString(); }
    Tab tab() { return tab; }
    List<LogEvent> investigationSnapshot() { return buffer.snapshot(); }
    void start() throws IOException {
        tailer.start();
        status.accept("Opened " + path.getFileName() + " in Fast View; loading a small tail window");
    }

    private BorderPane layout() {
        query.setPromptText("Find text or regular expression");
        trace.setPromptText("Trace / correlation ID");
        thread.setPromptText("Thread ID contains…");
        structured.setPromptText("field=value");
        structured.setPrefWidth(150);
        level.getItems().setAll(LogLevel.values());
        level.setValue(LogLevel.TRACE);
        level.setPrefWidth(105);
        query.setPrefWidth(250);
        trace.setPrefWidth(210);
        pauseButton.setOnAction(event -> togglePause());
        followTail.setSelected(true);
        followTail.selectedProperty().addListener((observable, old, selected) -> followTailChanged(selected));
        highlightingLink.setOnAction(event -> openHighlightRules());
        analyze.setTooltip(new Tooltip("Parse Java stack traces, levels, trace IDs, JSON, and redact secrets. Off keeps opening and rendering fastest."));
        analyze.selectedProperty().addListener((observable, old, enabled) -> {
            parser.reset();
            status.accept(enabled ? "Analyze enabled for new events" : "Fast View enabled for new events");
        });
        previousButton.setOnAction(event -> selectMatch(-1));
        nextButton.setOnAction(event -> selectMatch(1));
        historyButton.setOnAction(event -> chooseHistoryPosition());
        searchFileButton.setOnAction(event -> searchWholeFile());
        scratchButton.setDisable(true);
        scratchButton.setOnAction(event -> saveSearchToScratch());
        bookmarkButton.setOnAction(event -> bookmarkCurrent());
        scratchMode.getItems().setAll(ContextSelector.Mode.values());
        scratchMode.setValue(ContextSelector.Mode.MATCH_ONLY);
        scratchMode.setPrefWidth(125);
        Button clear = new Button("Clear");
        clear.setOnAction(event -> clear());
        filterMode.getItems().setAll(FilterMode.values());
        filterMode.setValue(FilterMode.SHOW_ALL);
        filterMode.setPrefWidth(105);
        onlyMatches.setVisible(false);
        onlyMatches.setManaged(false);
        savedSearches.getItems().setAll(preferences.savedSearches);
        savedSearches.setPromptText("Saved searches");
        savedSearches.setPrefWidth(145);
        savedSearches.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(AppPreferences.SavedSearch value) { return value == null ? "" : value.name(); }
            @Override public AppPreferences.SavedSearch fromString(String value) { return null; }
        });
        savedSearches.valueProperty().addListener((observable, old, selected) -> {
            if (selected != null) { query.setText(selected.expression()); regex.setSelected(selected.regex()); }
        });
        ToolBar searchBar = new ToolBar(new Label("Level"), level, new Label("Find"), query, regex, filterMode,
            previousButton, nextButton, matchCount, scratchMode, scratchButton, followTail, analyze, highlightingLink);
        ToolBar actionsBar = new ToolBar(new Label("Thread"), thread, new Label("Trace ID"), trace, new Label("Field"), structured, savedSearches,
            searchFileButton, historyButton, bookmarkButton, pauseButton, clear, count, indexStatus);

        details.setEditable(false);
        details.setWrapText(false);
        details.setWrapText(preferences.wrapLines);
        details.setStyle("-fx-font-size: " + preferences.fontSize + "px;");
        details.getStyleClass().add("details");
        SplitPane split = new SplitPane(table, details);
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(0.68);
        BorderPane pane = new BorderPane(split);
        pane.setTop(new VBox(searchBar, actionsBar));
        return pane;
    }

    @SuppressWarnings("unchecked")
    private void configureTable() {
        TableColumn<LogEvent, String> time = column("Received", event -> TIME.format(event.receivedAt()), 115);
        TableColumn<LogEvent, String> line = column("Line", event -> Long.toString(event.lineNumber()), 75);
        TableColumn<LogEvent, LogLevel> severity = new TableColumn<>("Level");
        severity.setCellValueFactory(value -> new ReadOnlyObjectWrapper<>(value.getValue().level()));
        severity.setPrefWidth(80);
        TableColumn<LogEvent, String> traceId = column("Trace", LogEvent::traceId, 190);
        TableColumn<LogEvent, String> threadId = column("Thread", LogEvent::threadId, 190);
        TableColumn<LogEvent, String> message = column("Message", LogEvent::summary, 700);
        message.setCellFactory(ignored -> new HighlightCell());
        TableColumn<LogEvent, String> groups = column("Groups", event -> String.join(" | ", searchPattern.groups(event.content())), 180);
        table.getColumns().setAll(time, line, severity, threadId, traceId, groups, message);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Waiting for log events…"));
        table.setRowFactory(ignored -> new TableRow<>() {
            @Override protected void updateItem(LogEvent item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("level-trace", "level-debug", "level-info", "level-warn", "level-error", "level-fatal", "level-unknown");
                setStyle("");
                if (!empty && item != null) {
                    getStyleClass().add("level-" + item.level().name().toLowerCase());
                    applyHighlightRule(this, item.content());
                    if (!searchPattern.empty() && searchPattern.valid() && searchPattern.matches(item.content())
                        && filterMode.getValue() == FilterMode.SHOW_ALL) {
                        setStyle("-fx-background-color: #ffe066; -fx-text-background-color: #101317;");
                    }
                }
            }
        });
        table.getSelectionModel().selectedItemProperty().addListener((observable, old, selected) -> showDetails(selected));
    }

    private TableColumn<LogEvent, String> column(String title, java.util.function.Function<LogEvent, String> value, double width) {
        TableColumn<LogEvent, String> column = new TableColumn<>(title);
        column.setCellValueFactory(item -> new ReadOnlyStringWrapper(value.apply(item.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private void configureFilters() {
        query.textProperty().addListener((observable, old, value) -> updateFilter());
        trace.textProperty().addListener((observable, old, value) -> updateFilter());
        thread.textProperty().addListener((observable, old, value) -> updateFilter());
        structured.textProperty().addListener((observable, old, value) -> updateFilter());
        regex.selectedProperty().addListener((observable, old, value) -> updateFilter());
        onlyMatches.selectedProperty().addListener((observable, old, value) -> updateFilter());
        filterMode.valueProperty().addListener((observable, old, value) -> updateFilter());
        level.valueProperty().addListener((observable, old, value) -> updateFilter());
    }

    private void updateFilter() {
        String value = query.getText();
        searchPattern = SearchPattern.compile(value, regex.isSelected());
        if (!searchPattern.valid()) {
            query.getStyleClass().add("invalid-filter");
            previousButton.setDisable(true);
            nextButton.setDisable(true);
            scratchButton.setDisable(true);
            matchCount.setText("Invalid regex");
            return;
        }
        query.getStyleClass().remove("invalid-filter");
        LogLevel minimum = level.getValue() == null ? LogLevel.TRACE : level.getValue();
        var base = new EventFilter("", false, minimum, trace.getText()).predicate();
        FilterMode mode = filterMode.getValue() == null ? FilterMode.SHOW_ALL : filterMode.getValue();
        String requiredThread = thread.getText().strip().toLowerCase(java.util.Locale.ROOT);
        String fieldExpression = structured.getText().strip();
        int equals = fieldExpression.indexOf('=');
        String fieldName = equals > 0 ? fieldExpression.substring(0, equals).strip() : "";
        String fieldValue = equals > 0 ? fieldExpression.substring(equals + 1).strip() : "";
        filtered.setPredicate(event -> base.test(event)
            && (requiredThread.isEmpty() || event.threadId().toLowerCase(java.util.Locale.ROOT).contains(requiredThread))
            && (fieldName.isEmpty() || fieldValue.equalsIgnoreCase(fieldExtractor.extract(event.content()).getOrDefault(fieldName, "")))
            && switch (mode) {
            case SHOW_ALL -> true;
            case INCLUDE_MATCHES -> searchPattern.matches(event.content());
            case EXCLUDE_MATCHES -> !searchPattern.matches(event.content());
        });
        boolean navigationDisabled = searchPattern.empty();
        previousButton.setDisable(navigationDisabled);
        nextButton.setDisable(navigationDisabled);
        scratchButton.setDisable(navigationDisabled);
        table.refresh();
        updateMatchCount();
        updateCount();
    }

    private void selectMatch(int direction) {
        if (searchPattern.empty() || !searchPattern.valid() || filtered.isEmpty()) return;
        int selected = table.getSelectionModel().getSelectedIndex();
        int start = selected < 0 ? (direction > 0 ? -1 : 0) : selected;
        for (int offset = 1; offset <= filtered.size(); offset++) {
            int index = Math.floorMod(start + direction * offset, filtered.size());
            if (searchPattern.matches(filtered.get(index).content())) {
                LogEvent match = filtered.get(index);
                table.scrollTo(index);
                showDetails(match);
                table.getSelectionModel().clearSelection();
                status.accept("Match " + (index + 1) + " of " + filtered.size() + " in " + path.getFileName());
                return;
            }
        }
        status.accept("No matches in " + path.getFileName());
    }

    void nextMatch() { selectMatch(1); }
    void previousMatch() { selectMatch(-1); }

    private void configureDetails() { details.setText("Select an event to view its complete content."); }

    private void showDetails(LogEvent event) {
        if (event == null) return;
        lastInspected = event;
        String content = event.content();
        String trimmed = content.strip();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try { content = mapper.writeValueAsString(mapper.readTree(trimmed)); }
            catch (IOException ignored) { /* retain original text */ }
        }
        details.setText(expandTabs(content));
        details.positionCaret(0);
    }

    private void accept(List<String> lines) {
        List<LogEvent> events = new java.util.ArrayList<>();
        if (analyze.isSelected()) lines.forEach(line -> events.addAll(parser.accept(line, ++lineNumber)));
        else lines.forEach(line -> events.add(rawEvent(line, ++lineNumber)));
        append(events);
        if (analyze.isSelected()) idleFlush.playFromStart();
    }

    private LogEvent rawEvent(String line, long sourceLine) {
        String summary = line.length() > 500 ? line.substring(0, 497) + "..." : line;
        return new LogEvent(++fastSequence, sourceLine, java.time.Instant.now(), fastLevel(line), fastThread(line), "", summary, line);
    }

    private String fastThread(String line) {
        int start = line.indexOf('[');
        if (start < 0) return "";
        int end = line.indexOf(']', start + 1);
        if (end < 0 || end - start > 161) return "";
        String candidate = line.substring(start + 1, end).strip();
        return candidate.toLowerCase(java.util.Locale.ROOT).contains("traceid=") ? "" : candidate;
    }

    private LogLevel fastLevel(String line) {
        if (line.contains("FATAL")) return LogLevel.FATAL;
        if (line.contains("ERROR")) return LogLevel.ERROR;
        if (line.contains("WARN")) return LogLevel.WARN;
        if (line.contains("INFO")) return LogLevel.INFO;
        if (line.contains("DEBUG")) return LogLevel.DEBUG;
        if (line.contains("TRACE")) return LogLevel.TRACE;
        return LogLevel.UNKNOWN;
    }

    private void append(List<LogEvent> events) {
        if (events.isEmpty()) return;
        checkLiveAlerts(events);
        buffer.addAll(events);
        tailError = false;
        if (!tab.isSelected()) unread = true;
        updateTabTitle();
        if (!paused) {
            displayed.addAll(events);
            int overflow = displayed.size() - CAPACITY;
            if (overflow > 0) displayed.remove(0, overflow);
            if (followTail.isSelected()) table.scrollTo(Math.max(0, filtered.size() - 1));
        }
        updateCount();
    }

    void togglePause() {
        paused = !paused;
        if (paused && followTail.isSelected()) followTail.setSelected(false);
        pauseButton.setText(paused ? "Resume" : "Pause");
        updateTabTitle();
        if (!paused) {
            displayed.setAll(buffer.snapshot()); updateCount();
            if (followTail.isSelected()) table.scrollTo(Math.max(0, filtered.size() - 1));
        }
        status.accept(paused ? "Paused display for " + path.getFileName() : "Following " + path);
    }

    void clear() {
        idleFlush.stop();
        parser.reset();
        buffer.clear();
        displayed.clear();
        details.clear();
        lineNumber = 0;
        updateCount();
    }
    void focusFilter() { query.requestFocus(); query.selectAll(); }
    void toggleFollowTail() { followTail.setSelected(!followTail.isSelected()); }

    private void followTailChanged(boolean following) {
        if (following) {
            if (paused) {
                paused = false;
                pauseButton.setText("Pause");
                displayed.setAll(buffer.snapshot());
                updateTabTitle();
                updateCount();
            }
            table.getSelectionModel().clearSelection();
            table.scrollTo(Math.max(0, filtered.size() - 1));
            status.accept("Following live tail for " + path.getFileName());
        } else status.accept((paused ? "Display paused for " : "Follow Tail off for ") + path.getFileName()
            + (paused ? "" : "; new events continue loading"));
    }

    private void checkLiveAlerts(List<LogEvent> events) {
        long now = System.currentTimeMillis();
        for (AppPreferences.AlertRule rule : preferences.alertRules) {
            if (!rule.enabled()) continue;
            SearchPattern pattern = SearchPattern.compile(rule.expression(), rule.regex(), rule.ignoreCase());
            for (LogEvent event : events) {
                if (!pattern.matches(event.content())) continue;
                long previous = lastAlertAt.getOrDefault(rule.expression(), 0L);
                if (now - previous >= 5_000) {
                    lastAlertAt.put(rule.expression(), now);
                    DesktopNotifier.notify("TraceTail alert: " + rule.expression(), path.getFileName() + ": " + event.summary());
                    status.accept("Live alert matched " + rule.expression() + " in " + path.getFileName());
                }
                if (rule.autoScratch()) saveAlertToScratch(rule, event);
            }
        }
    }

    private void saveAlertToScratch(AppPreferences.AlertRule rule, LogEvent event) {
        try {
            Files.createDirectories(scratchDirectory.get());
            String name = rule.expression().replaceAll("[^A-Za-z0-9._-]", "_"); if (name.length() > 32) name = name.substring(0, 32);
            Path destination = scratchDirectory.get().resolve("alert-" + name + "-" + System.currentTimeMillis() + ".log");
            Files.writeString(destination, event.content() + System.lineSeparator(), StandardCharsets.UTF_8);
            scratchCreated.accept(destination);
        } catch (IOException exception) { status.accept("Could not auto-save alert: " + exception.getMessage()); }
    }
    void saveCurrentSearch() {
        String expression = query.getText().strip();
        if (expression.isEmpty() || !searchPattern.valid()) { status.accept("Enter a valid search before saving it"); return; }
        String name = expression.length() > 28 ? expression.substring(0, 28) + "…" : expression;
        preferences.savedSearches.removeIf(item -> item.name().equalsIgnoreCase(name));
        AppPreferences.SavedSearch saved = new AppPreferences.SavedSearch(name, expression, regex.isSelected());
        preferences.savedSearches.add(saved);
        savedSearches.getItems().setAll(preferences.savedSearches);
        savedSearches.setValue(saved);
        savePreferences.run();
        status.accept("Saved search: " + name);
    }

    void deleteCurrentSavedSearch() {
        AppPreferences.SavedSearch selected = savedSearches.getValue();
        if (selected == null) return;
        preferences.savedSearches.remove(selected);
        savedSearches.getItems().setAll(preferences.savedSearches);
        savePreferences.run();
        status.accept("Deleted saved search: " + selected.name());
    }

    void copyResults() {
        try {
            ClipboardContent content = new ClipboardContent();
            content.putString(SearchResultExporter.export(List.copyOf(filtered), searchPattern, SearchResultExporter.Format.TEXT));
            Clipboard.getSystemClipboard().setContent(content);
            status.accept("Copied " + filtered.size() + " visible results");
        } catch (IOException exception) { status.accept("Could not copy results: " + exception.getMessage()); }
    }

    void exportResults(Path destination, SearchResultExporter.Format format) throws IOException {
        Files.writeString(destination, SearchResultExporter.export(List.copyOf(filtered), searchPattern, format), StandardCharsets.UTF_8);
        status.accept("Exported " + filtered.size() + " visible results to " + destination);
    }

    void applyDisplayPreferences() {
        details.setWrapText(preferences.wrapLines);
        details.setStyle("-fx-font-size: " + preferences.fontSize + "px;");
        table.setStyle("-fx-font-size: " + preferences.fontSize + "px;");
        table.refresh();
    }

    private void openHighlightRules() {
        if (!HighlightRulesDialog.show(preferences.highlightRules)) return;
        savePreferences.run();
        refreshAllHighlighting.run();
        status.accept("Updated highlight rules");
    }

    void refreshHighlighting() { table.refresh(); }

    private void chooseHistoryPosition() {
        TextInputDialog dialog = new TextInputDialog("50%");
        dialog.setTitle("Open large-file history");
        dialog.setHeaderText("Open a fixed-memory page from anywhere in the file");
        dialog.setContentText("Line number or percentage (for example 250000 or 50%):");
        dialog.showAndWait().ifPresent(value -> {
            String requested = value.strip();
            if (requested.endsWith("%")) {
                try { loadHistoryPercent(Double.parseDouble(requested.substring(0, requested.length() - 1)) / 100.0); }
                catch (IllegalArgumentException exception) { status.accept("Could not open history: " + exception.getMessage()); }
            } else {
                try { loadIndexedPage(Math.max(0, Long.parseLong(requested) - 1)); }
                catch (IllegalArgumentException exception) { status.accept("Could not open history: " + exception.getMessage()); }
            }
        });
    }

    private void loadHistoryPercent(double fraction) {
        if (fraction < 0 || fraction > 1) throw new IllegalArgumentException("percentage must be between 0 and 100");
        SparseLineIndex.Snapshot snapshot = lineIndex.snapshot();
        if (snapshot.complete()) {
            loadIndexedPage((long) (Math.max(0, snapshot.lineCount() - 1) * fraction));
            return;
        }
        pageWorker.execute(() -> {
            try {
                Charset charset = Charset.forName(preferences.charset);
                FileWindowReader.Window window = new FileWindowReader().read(path, fraction, charset);
                Platform.runLater(() -> displayWindow(window));
            } catch (IOException exception) { Platform.runLater(() -> status.accept("Could not open history: " + exception.getMessage())); }
        });
        status.accept("Opening byte-position history while the line index continues in the background");
    }

    private void displayWindow(FileWindowReader.Window window) {
        LogEventParser historyParser = new LogEventParser();
        List<LogEvent> history = new java.util.ArrayList<>();
        long historyLine = 0;
        for (String line : window.lines()) history.addAll(historyParser.accept(line, ++historyLine));
        history.addAll(historyParser.finish());
        paused = true;
        followTail.setSelected(false);
        pauseButton.setText("Resume tail");
        updateTabTitle();
        displayed.setAll(history);
        updateCount();
        updateMatchCount();
        table.scrollTo(0);
        status.accept("Viewing bytes " + window.startOffset() + "–" + window.endOffset() + " of " + window.fileSize() + "; resume to return to the live tail");
    }

    private void loadIndexedPage(long requestedLine) {
        SparseLineIndex.Snapshot snapshot = lineIndex.snapshot();
        if (!snapshot.complete()) {
            ensureIndexStarted();
            status.accept("Building the line index on demand; use a percentage immediately or retry the line number when indexing completes");
            return;
        }
        if (!snapshot.complete() && requestedLine >= snapshot.lineCount()) {
            status.accept("That line is not indexed yet (currently " + snapshot.lineCount() + " lines); use a percentage or wait for indexing");
            return;
        }
        status.accept("Loading line " + (requestedLine + 1) + " from disk…");
        pageWorker.execute(() -> {
            try {
                PagedLineReader.Page page = pagedReader.readPage(requestedLine);
                List<LogEvent> events = parsePage(page);
                Platform.runLater(() -> displayPage(page, events));
            } catch (IOException exception) { Platform.runLater(() -> status.accept("Could not load page: " + exception.getMessage())); }
        });
    }

    private List<LogEvent> parsePage(PagedLineReader.Page page) {
        LogEventParser pageParser = new LogEventParser();
        List<LogEvent> events = new java.util.ArrayList<>();
        for (PagedLineReader.Line line : page.lines()) events.addAll(pageParser.accept(line.text(), line.lineNumber() + 1));
        events.addAll(pageParser.finish());
        return events;
    }

    private void displayPage(PagedLineReader.Page page, List<LogEvent> events) {
        paused = true; followTail.setSelected(false); pauseButton.setText("Resume tail"); updateTabTitle();
        displayed.setAll(events); updateCount(); table.scrollTo(0);
        long end = page.lines().isEmpty() ? page.startLine() : page.lines().getLast().lineNumber();
        status.accept("Viewing lines " + (page.startLine() + 1) + "–" + (end + 1) + " from disk; resume to return to live tail");
    }

    private void updateIndexStatus(SparseLineIndex.Snapshot snapshot) {
        if (snapshot.failure() != null) indexStatus.setText("Index error");
        else if (snapshot.complete()) {
            indexStatus.setText(formatCount(snapshot.lineCount()) + " lines indexed");
            if (pendingWholeFileSearch) { pendingWholeFileSearch = false; searchWholeFile(); }
        }
        else indexStatus.setText("Index " + Math.round(snapshot.progress() * 100) + "%");
    }

    private void ensureIndexStarted() {
        if (indexStarted) return;
        indexStarted = true;
        indexStatus.setText("Index starting…");
        lineIndex.start();
    }

    private void searchWholeFile() {
        if (searchCancellation != null) {
            searchCancellation.set(true);
            searchFileButton.setDisable(true);
            return;
        }
        SparseLineIndex.Snapshot snapshot = lineIndex.snapshot();
        if (!snapshot.complete()) {
            pendingWholeFileSearch = true;
            ensureIndexStarted();
            status.accept("Building the line index on demand; whole-file search will start automatically");
            return;
        }
        if (!searchPattern.valid() || searchPattern.empty()) { status.accept("Enter a valid Find expression first"); return; }
        AtomicBoolean cancellation = new AtomicBoolean();
        searchCancellation = cancellation;
        searchFileButton.setText("Cancel Search");
        SearchPattern requestedPattern = searchPattern;
        status.accept("Searching " + formatCount(snapshot.lineCount()) + " lines in the background…");
        pageWorker.execute(() -> {
            try {
                LargeFileSearcher searcher = new LargeFileSearcher(pagedReader, snapshot.lineCount(), 5_000);
                LargeFileSearcher.Result result = searcher.search(requestedPattern, 10_000, cancellation,
                    progress -> Platform.runLater(() -> indexStatus.setText("Search " + Math.round(progress * 100) + "%")));
                List<LogEvent> events = parseSearchResults(result.matches());
                Platform.runLater(() -> displaySearchResults(result, events));
            } catch (IOException exception) {
                Platform.runLater(() -> { finishSearchButton(); status.accept("Whole-file search failed: " + exception.getMessage()); });
            }
        });
    }

    private List<LogEvent> parseSearchResults(List<PagedLineReader.Line> lines) {
        List<LogEvent> events = new java.util.ArrayList<>(lines.size());
        for (PagedLineReader.Line line : lines) {
            LogEventParser oneLine = new LogEventParser();
            oneLine.accept(line.text(), line.lineNumber() + 1);
            events.addAll(oneLine.finish());
        }
        return events;
    }

    private void displaySearchResults(LargeFileSearcher.Result result, List<LogEvent> events) {
        finishSearchButton();
        if (result.cancelled()) { status.accept("Whole-file search cancelled"); return; }
        paused = true; followTail.setSelected(false); pauseButton.setText("Resume tail"); updateTabTitle();
        displayed.setAll(events); updateCount(); table.scrollTo(0);
        status.accept("Whole-file search found " + result.matches().size() + " lines" + (result.truncated() ? " (limited to 10,000)" : ""));
    }

    private void finishSearchButton() {
        searchCancellation = null; searchFileButton.setText("Search File"); searchFileButton.setDisable(false);
        updateIndexStatus(lineIndex.snapshot());
    }

    private String formatCount(long value) {
        if (value >= 1_000_000_000) return String.format("%.1fB", value / 1_000_000_000.0);
        if (value >= 1_000_000) return String.format("%.1fM", value / 1_000_000.0);
        if (value >= 1_000) return String.format("%.1fK", value / 1_000.0);
        return Long.toString(value);
    }

    private void applyHighlightRule(TableRow<LogEvent> row, String content) {
        for (AppPreferences.HighlightRule rule : preferences.highlightRules) {
            if (ruleMatches(rule, content)) {
                row.setStyle("-fx-background-color: " + rule.background() + "; -fx-text-background-color: " + rule.foreground()
                    + "; -fx-font-weight: " + (rule.bold() ? "bold" : "normal")
                    + "; -fx-font-style: " + (rule.italic() ? "italic" : "normal") + ";");
                row.setTooltip(new Tooltip("Highlight rule: " + rule.name()));
                return;
            }
        }
        row.setTooltip(null);
    }

    private boolean ruleMatches(AppPreferences.HighlightRule rule, String content) {
        if (!rule.enabled()) return false;
        SearchPattern candidate = SearchPattern.compile(rule.expression(), rule.regex(), rule.ignoreCase());
        boolean matches = candidate.matches(content);
        return rule.invert() ? !matches : matches;
    }

    private void saveSearchToScratch() {
        if (searchPattern.empty() || !searchPattern.valid()) { status.accept("Enter a valid Find expression first"); return; }
        List<LogEvent> selected = ContextSelector.select(List.copyOf(displayed), searchPattern, scratchMode.getValue());
        if (selected.isEmpty()) { status.accept("No matching rows to save"); return; }
        try {
            Files.createDirectories(scratchDirectory.get());
            String queryName = query.getText().strip().replaceAll("[^A-Za-z0-9._-]", "_");
            if (queryName.length() > 40) queryName = queryName.substring(0, 40);
            String base = path.getFileName().toString().replaceAll("[^A-Za-z0-9._-]", "_") + "-" + queryName;
            String stamp = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
            Path destination = scratchDirectory.get().resolve(base + "-scratch-" + stamp + ".log");
            StringBuilder output = new StringBuilder();
            for (LogEvent event : selected) output.append(event.content()).append(System.lineSeparator());
            Files.writeString(destination, output, StandardCharsets.UTF_8);
            scratchCreated.accept(destination);
            status.accept("Saved " + selected.size() + " search matches to Scratch: " + destination.getFileName());
        } catch (IOException exception) { status.accept("Could not save search to Scratch: " + exception.getMessage()); }
    }

    private void bookmarkCurrent() {
        LogEvent event = table.getSelectionModel().getSelectedItem();
        if (event == null) event = lastInspected;
        if (event == null) { status.accept("Select an event to bookmark"); return; }
        TextInputDialog dialog = new TextInputDialog(); dialog.setTitle("Add Bookmark");
        dialog.setHeaderText(path.getFileName() + " — line " + event.lineNumber()); dialog.setContentText("Investigation note:");
        LogEvent selected = event;
        dialog.showAndWait().ifPresent(note -> {
            String preview = selected.summary().length() > 200 ? selected.summary().substring(0, 200) : selected.summary();
            preferences.bookmarks.add(new AppPreferences.Bookmark(path.toString(), selected.lineNumber(), note.strip(), preview, java.time.Instant.now().toString()));
            savePreferences.run(); status.accept("Bookmarked line " + selected.lineNumber());
        });
    }
    private void updateCount() {
        count.setText(filtered.size() + " shown / " + buffer.size() + " retained");
        updateMatchCount();
    }
    private void updateMatchCount() {
        if (searchPattern.empty()) { matchCount.setText("No search"); return; }
        long matches = displayed.stream().filter(event -> searchPattern.matches(event.content())).count();
        matchCount.setText(matches + (matches == 1 ? " match" : " matches"));
    }
    @Override public void close() {
        idleFlush.stop();
        if (searchCancellation != null) searchCancellation.set(true);
        tailer.close(); lineIndex.close(); pageWorker.shutdownNow();
    }

    private final class HighlightCell extends TableCell<LogEvent, String> {
        @Override protected void updateItem(String value, boolean empty) {
            super.updateItem(value, empty);
            setText(null);
            setGraphic(null);
            if (empty || value == null) return;
            value = expandTabs(value);
            List<SearchPattern.Range> ranges = searchPattern.ranges(value);
            if (ranges.isEmpty()) {
                setText(value);
                return;
            }
            HBox flow = new HBox();
            flow.setAlignment(Pos.CENTER_LEFT);
            flow.setMaxHeight(Region.USE_PREF_SIZE);
            int cursor = 0;
            for (SearchPattern.Range range : ranges) {
                if (range.start() > cursor) flow.getChildren().add(segment(value.substring(cursor, range.start()), false));
                Label match = new Label(value.substring(range.start(), range.end()));
                match.getStyleClass().add("search-match");
                match.setMinHeight(Region.USE_PREF_SIZE);
                match.setMaxHeight(Region.USE_PREF_SIZE);
                flow.getChildren().add(match);
                cursor = range.end();
            }
            if (cursor < value.length()) flow.getChildren().add(segment(value.substring(cursor), false));
            setGraphic(flow);
        }

        private Label segment(String value, boolean highlighted) {
            Label label = new Label(value);
            label.setMinHeight(Region.USE_PREF_SIZE);
            label.setMaxHeight(Region.USE_PREF_SIZE);
            if (highlighted) label.getStyleClass().add("search-match");
            return label;
        }
    }

    private String expandTabs(String value) { return value.replace("\t", " ".repeat(Math.max(1, preferences.tabWidth))); }

    private void updateTabTitle() {
        String marker = tailError ? " ⚠" : paused ? " ⏸" : unread ? " ●" : "";
        tab.setText(path.getFileName() + marker);
    }
}
