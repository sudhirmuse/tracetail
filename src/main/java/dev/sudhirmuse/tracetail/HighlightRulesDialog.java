/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail;

import dev.sudhirmuse.tracetail.core.SearchPattern;
import dev.sudhirmuse.tracetail.settings.AppPreferences;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.List;

final class HighlightRulesDialog {
    private HighlightRulesDialog() { }

    static boolean show(List<AppPreferences.HighlightRule> target) {
        ObservableList<AppPreferences.HighlightRule> rules = FXCollections.observableArrayList(target);
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Highlighting");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ListView<AppPreferences.HighlightRule> list = new ListView<>(rules);
        list.setPrefSize(650, 250);
        list.setCellFactory(ignored -> new RuleCell());

        TextField expression = new TextField();
        expression.setPromptText("Text to highlight");
        CheckBox regex = new CheckBox("Regular Expression");
        CheckBox ignoreCase = new CheckBox("Ignore Case"); ignoreCase.setSelected(true);
        CheckBox invert = new CheckBox("Invert Match");
        CheckBox bold = new CheckBox("Bold");
        CheckBox italic = new CheckBox("Italic");
        CheckBox enabled = new CheckBox("Enabled"); enabled.setSelected(true);
        ColorPicker foreground = new ColorPicker(Color.BLACK);
        ColorPicker background = new ColorPicker(Color.CYAN);

        Button add = new Button("Add");
        Button delete = new Button("Delete");
        Button moveUp = new Button("Move Up");
        Button moveDown = new Button("Move Down");
        for (Button button : List.of(add, delete, moveUp, moveDown)) button.setMaxWidth(Double.MAX_VALUE);
        HBox controls = new HBox(12, add, delete, moveUp, moveDown);
        controls.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));

        GridPane colours = new GridPane(); colours.setHgap(18); colours.setVgap(5);
        colours.add(new Label("Foreground Color:"), 0, 0); colours.add(new Label("Background Color:"), 1, 0);
        colours.add(foreground, 0, 1); colours.add(background, 1, 1);
        foreground.setMaxWidth(Double.MAX_VALUE); background.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(foreground, Priority.ALWAYS); GridPane.setHgrow(background, Priority.ALWAYS);

        GridPane match = new GridPane(); match.setHgap(8); match.setVgap(7);
        match.add(new Label("String:"), 0, 0); match.add(expression, 1, 0, 5, 1);
        match.add(ignoreCase, 0, 1); match.add(invert, 1, 1); match.add(bold, 2, 1);
        match.add(italic, 3, 1); match.add(regex, 4, 1); match.add(enabled, 5, 1);
        GridPane.setHgrow(expression, Priority.ALWAYS);

        Runnable clear = () -> {
            expression.clear(); regex.setSelected(false); ignoreCase.setSelected(true);
            invert.setSelected(false); bold.setSelected(false); italic.setSelected(false); enabled.setSelected(true);
            foreground.setValue(Color.BLACK); background.setValue(Color.CYAN); list.getSelectionModel().clearSelection();
        };
        Runnable saveSelection = () -> {
            AppPreferences.HighlightRule rule = read(expression, regex, ignoreCase, invert, foreground, background, bold, italic, enabled);
            if (rule == null) return;
            int selected = list.getSelectionModel().getSelectedIndex();
            if (selected >= 0) rules.set(selected, rule); else rules.add(rule);
            list.getSelectionModel().select(rule);
        };
        add.setOnAction(event -> {
            AppPreferences.HighlightRule rule = new AppPreferences.HighlightRule("New highlight", "New highlight", false, true, false,
                hex(background.getValue()), hex(foreground.getValue()), false, false, true);
            rules.add(rule); list.getSelectionModel().select(rule); expression.requestFocus(); expression.selectAll();
        });
        delete.setOnAction(event -> { int index = list.getSelectionModel().getSelectedIndex(); if (index >= 0) rules.remove(index); clear.run(); });
        moveUp.setOnAction(event -> move(rules, list, -1));
        moveDown.setOnAction(event -> move(rules, list, 1));

        list.getSelectionModel().selectedItemProperty().addListener((observable, old, rule) -> {
            if (rule == null) return;
            expression.setText(rule.expression()); regex.setSelected(rule.regex());
            ignoreCase.setSelected(rule.ignoreCase()); invert.setSelected(rule.invert()); bold.setSelected(rule.bold());
            italic.setSelected(rule.italic()); enabled.setSelected(rule.enabled());
            foreground.setValue(safeColor(rule.foreground(), Color.BLACK)); background.setValue(safeColor(rule.background(), Color.CYAN));
        });
        Runnable updateSelected = () -> {
            int index = list.getSelectionModel().getSelectedIndex();
            if (index < 0) return;
            AppPreferences.HighlightRule rule = read(expression, regex, ignoreCase, invert, foreground, background, bold, italic, enabled);
            if (rule != null) { rules.set(index, rule); list.getSelectionModel().select(index); }
        };
        expression.focusedProperty().addListener((o, old, focused) -> { if (!focused) updateSelected.run(); });
        for (CheckBox box : List.of(regex, ignoreCase, invert, bold, italic, enabled)) box.selectedProperty().addListener((o, old, value) -> updateSelected.run());
        foreground.valueProperty().addListener((o, old, value) -> updateSelected.run());
        background.valueProperty().addListener((o, old, value) -> updateSelected.run());

        VBox content = new VBox(10, list, controls, colours, match); content.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(content); dialog.getDialogPane().setPrefWidth(700);
        if (!rules.isEmpty()) list.getSelectionModel().selectFirst();
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return false;
        updateSelected.run(); target.clear(); target.addAll(rules); return true;
    }

    private static void move(ObservableList<AppPreferences.HighlightRule> rules, ListView<AppPreferences.HighlightRule> list, int direction) {
        int from = list.getSelectionModel().getSelectedIndex(); int to = from + direction;
        if (from < 0 || to < 0 || to >= rules.size()) return;
        AppPreferences.HighlightRule rule = rules.remove(from); rules.add(to, rule); list.getSelectionModel().select(to); list.scrollTo(to);
    }

    private static AppPreferences.HighlightRule read(TextField expression, CheckBox regex, CheckBox ignoreCase,
        CheckBox invert, ColorPicker foreground, ColorPicker background, CheckBox bold, CheckBox italic, CheckBox enabled) {
        String value = expression.getText().strip();
        if (value.isEmpty() || !SearchPattern.compile(value, regex.isSelected(), ignoreCase.isSelected()).valid()) {
            expression.setStyle("-fx-border-color: #ff5d73;"); return null;
        }
        expression.setStyle("");
        return new AppPreferences.HighlightRule(value, value, regex.isSelected(), ignoreCase.isSelected(), invert.isSelected(),
            hex(background.getValue()), hex(foreground.getValue()), bold.isSelected(), italic.isSelected(), enabled.isSelected());
    }

    private static Color safeColor(String value, Color fallback) { try { return Color.web(value); } catch (IllegalArgumentException exception) { return fallback; } }
    private static String hex(Color color) { return String.format("#%02X%02X%02X", Math.round(color.getRed()*255), Math.round(color.getGreen()*255), Math.round(color.getBlue()*255)); }

    private static final class RuleCell extends ListCell<AppPreferences.HighlightRule> {
        @Override protected void updateItem(AppPreferences.HighlightRule rule, boolean empty) {
            super.updateItem(rule, empty);
            if (empty || rule == null) { setGraphic(null); setText(null); setStyle(""); return; }
            Rectangle swatch = new Rectangle(170, 23, safeColor(rule.background(), Color.CYAN));
            Label sample = new Label(rule.expression());
            sample.setTextFill(safeColor(rule.foreground(), Color.BLACK));
            sample.setStyle("-fx-font-weight:" + (rule.bold() ? "bold" : "normal") + ";-fx-font-style:" + (rule.italic() ? "italic" : "normal"));
            HBox overlay = new HBox(sample); overlay.setPadding(new Insets(2, 5, 2, 5));
            javafx.scene.layout.StackPane preview = new javafx.scene.layout.StackPane(swatch, overlay);
            Label description = new Label(rule.expression() + (rule.invert() ? " — inverted" : "") + (rule.enabled() ? "" : " — disabled"));
            setGraphic(new HBox(10, preview, description)); setText(null);
        }
    }
}
