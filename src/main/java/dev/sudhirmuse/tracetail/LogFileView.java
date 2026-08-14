/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.sudhirmuse.tracetail.core.BoundedEventBuffer;
import dev.sudhirmuse.tracetail.core.EventFilter;
import dev.sudhirmuse.tracetail.core.FileTailer;
import dev.sudhirmuse.tracetail.core.LogEvent;
import dev.sudhirmuse.tracetail.core.LogEventParser;
import dev.sudhirmuse.tracetail.core.LogLevel;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

final class LogFileView implements AutoCloseable {
    private static final int CAPACITY = 20_000;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());
    private final Path path;
    private final Consumer<String> status;
    private final Tab tab;
    private final ObservableList<LogEvent> displayed = FXCollections.observableArrayList();
    private final FilteredList<LogEvent> filtered = new FilteredList<>(displayed);
    private final BoundedEventBuffer buffer = new BoundedEventBuffer(CAPACITY);
    private final LogEventParser parser = new LogEventParser();
    private final TableView<LogEvent> table = new TableView<>(filtered);
    private final TextArea details = new TextArea();
    private final TextField query = new TextField();
    private final TextField trace = new TextField();
    private final CheckBox regex = new CheckBox("Regex");
    private final ComboBox<LogLevel> level = new ComboBox<>();
    private final Button pauseButton = new Button("Pause");
    private final Label count = new Label("0 events");
    private final PauseTransition idleFlush = new PauseTransition(Duration.millis(400));
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final FileTailer tailer;
    private boolean paused;

    LogFileView(Path path, Consumer<String> status) throws IOException {
        this.path = path;
        this.status = status;
        this.tab = new Tab(path.getFileName().toString());
        this.tab.setTooltip(new javafx.scene.control.Tooltip(path.toString()));
        this.tab.setClosable(true);
        this.tailer = new FileTailer(path, lines -> Platform.runLater(() -> accept(lines)),
            exception -> Platform.runLater(() -> status.accept("Tail error for " + path.getFileName() + ": " + exception.getMessage())));
        configureTable();
        configureFilters();
        configureDetails();
        tab.setContent(layout());
        idleFlush.setOnFinished(event -> append(parser.finish()));
    }

    Path path() { return path; }
    Tab tab() { return tab; }
    void start() throws IOException { tailer.start(); status.accept("Following " + path); }

    private BorderPane layout() {
        query.setPromptText("Filter text or expression");
        trace.setPromptText("Trace / correlation ID");
        level.getItems().setAll(LogLevel.values());
        level.setValue(LogLevel.TRACE);
        level.setPrefWidth(105);
        query.setPrefWidth(320);
        trace.setPrefWidth(210);
        pauseButton.setOnAction(event -> togglePause());
        Button clear = new Button("Clear");
        clear.setOnAction(event -> clear());
        ToolBar filters = new ToolBar(new Label("Level"), level, query, regex, trace, pauseButton, clear, count);

        details.setEditable(false);
        details.setWrapText(false);
        details.getStyleClass().add("details");
        SplitPane split = new SplitPane(table, details);
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(0.68);
        BorderPane pane = new BorderPane(split);
        pane.setTop(filters);
        return pane;
    }

    @SuppressWarnings("unchecked")
    private void configureTable() {
        TableColumn<LogEvent, String> time = column("Received", event -> TIME.format(event.receivedAt()), 115);
        TableColumn<LogEvent, LogLevel> severity = new TableColumn<>("Level");
        severity.setCellValueFactory(value -> new ReadOnlyObjectWrapper<>(value.getValue().level()));
        severity.setPrefWidth(80);
        TableColumn<LogEvent, String> traceId = column("Trace", LogEvent::traceId, 190);
        TableColumn<LogEvent, String> message = column("Message", LogEvent::summary, 700);
        table.getColumns().setAll(time, severity, traceId, message);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Waiting for log events…"));
        table.setRowFactory(ignored -> new TableRow<>() {
            @Override protected void updateItem(LogEvent item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("level-trace", "level-debug", "level-info", "level-warn", "level-error", "level-fatal", "level-unknown");
                if (!empty && item != null) getStyleClass().add("level-" + item.level().name().toLowerCase());
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
        regex.selectedProperty().addListener((observable, old, value) -> updateFilter());
        level.valueProperty().addListener((observable, old, value) -> updateFilter());
    }

    private void updateFilter() {
        String value = query.getText();
        if (regex.isSelected() && !EventFilter.validRegex(value)) {
            query.getStyleClass().add("invalid-filter");
            return;
        }
        query.getStyleClass().remove("invalid-filter");
        LogLevel minimum = level.getValue() == null ? LogLevel.TRACE : level.getValue();
        filtered.setPredicate(new EventFilter(value, regex.isSelected(), minimum, trace.getText()).predicate());
        updateCount();
    }

    private void configureDetails() { details.setText("Select an event to view its complete content."); }

    private void showDetails(LogEvent event) {
        if (event == null) return;
        String content = event.content();
        String trimmed = content.strip();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try { content = mapper.writeValueAsString(mapper.readTree(trimmed)); }
            catch (IOException ignored) { /* retain original text */ }
        }
        details.setText(content);
        details.positionCaret(0);
    }

    private void accept(List<String> lines) {
        List<LogEvent> events = new java.util.ArrayList<>();
        lines.forEach(line -> events.addAll(parser.accept(line)));
        append(events);
        idleFlush.playFromStart();
    }

    private void append(List<LogEvent> events) {
        if (events.isEmpty()) return;
        buffer.addAll(events);
        if (!paused) {
            displayed.addAll(events);
            int overflow = displayed.size() - CAPACITY;
            if (overflow > 0) displayed.remove(0, overflow);
            if (!table.isFocused() || table.getSelectionModel().isEmpty()) table.scrollTo(Math.max(0, filtered.size() - 1));
        }
        updateCount();
    }

    void togglePause() {
        paused = !paused;
        pauseButton.setText(paused ? "Resume" : "Pause");
        tab.setText(path.getFileName() + (paused ? " ⏸" : ""));
        if (!paused) { displayed.setAll(buffer.snapshot()); updateCount(); table.scrollTo(Math.max(0, filtered.size() - 1)); }
        status.accept(paused ? "Paused display for " + path.getFileName() : "Following " + path);
    }

    void clear() {
        idleFlush.stop();
        parser.reset();
        buffer.clear();
        displayed.clear();
        details.clear();
        updateCount();
    }
    void focusFilter() { query.requestFocus(); query.selectAll(); }
    private void updateCount() { count.setText(filtered.size() + " shown / " + buffer.size() + " retained"); }
    @Override public void close() { idleFlush.stop(); tailer.close(); }
}
