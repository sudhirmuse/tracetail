/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail;

import dev.sudhirmuse.tracetail.core.LogLevel;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

final class LogHistogramDialog {
    private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private LogHistogramDialog() { }

    static void show(Stage owner, List<InvestigationEvent> events, Consumer<Path> openFile, String theme) {
        if (events.isEmpty()) { new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION, "Open logs before creating a histogram.").showAndWait(); return; }
        long first = events.getFirst().timestamp().getEpochSecond();
        long last = events.getLast().timestamp().getEpochSecond();
        long width = Math.max(1, (last - first + 1) / 40);
        Map<Long, Bucket> buckets = new LinkedHashMap<>();
        for (InvestigationEvent event : events) {
            long key = Math.floorDiv(event.timestamp().getEpochSecond() - first, width) * width + first;
            buckets.computeIfAbsent(key, ignored -> new Bucket()).events.add(event);
        }
        CategoryAxis x = new CategoryAxis(); NumberAxis y = new NumberAxis(); y.setLabel("Events");
        BarChart<String, Number> chart = new BarChart<>(x, y); chart.setAnimated(false); chart.setTitle("Log severity over time");
        XYChart.Series<String, Number> errors = new XYChart.Series<>(); errors.setName("Errors");
        XYChart.Series<String, Number> warnings = new XYChart.Series<>(); warnings.setName("Warnings");
        XYChart.Series<String, Number> others = new XYChart.Series<>(); others.setName("Other");
        Map<XYChart.Data<String, Number>, Bucket> links = new LinkedHashMap<>();
        buckets.forEach((start, bucket) -> {
            String label = LABEL.format(Instant.ofEpochSecond(start));
            XYChart.Data<String, Number> error = new XYChart.Data<>(label, bucket.count(LogLevel.ERROR) + bucket.count(LogLevel.FATAL));
            XYChart.Data<String, Number> warning = new XYChart.Data<>(label, bucket.count(LogLevel.WARN));
            XYChart.Data<String, Number> other = new XYChart.Data<>(label, bucket.events.size() - error.getYValue().intValue() - warning.getYValue().intValue());
            errors.getData().add(error); warnings.getData().add(warning); others.getData().add(other);
            links.put(error, bucket); links.put(warning, bucket); links.put(other, bucket);
        });
        chart.getData().setAll(java.util.List.of(others, warnings, errors));
        Label hint = new Label("Click a bar to open that time bucket in the merged timeline.");
        BorderPane root = new BorderPane(chart); root.setBottom(hint);
        Stage stage = new Stage(); stage.initOwner(owner); stage.setTitle("Log Histogram"); Scene scene = new Scene(root, 1100, 650); ThemeSupport.apply(scene, theme); stage.setScene(scene); stage.show();
        Platform.runLater(() -> links.forEach((data, bucket) -> {
            if (data.getNode() != null) data.getNode().setOnMouseClicked(event -> MergedTimelineDialog.show(stage, bucket.events, openFile, theme));
        }));
    }

    private static final class Bucket {
        private final List<InvestigationEvent> events = new ArrayList<>();
        int count(LogLevel level) { return (int) events.stream().filter(item -> item.event().level() == level).count(); }
    }
}
