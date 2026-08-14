/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail;

import dev.sudhirmuse.tracetail.core.SearchPattern;
import dev.sudhirmuse.tracetail.settings.AppPreferences;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import java.util.List;

final class LiveAlertsDialog {
    private LiveAlertsDialog() { }
    static boolean show(List<AppPreferences.AlertRule> target) {
        var rules = FXCollections.observableArrayList(target);
        Dialog<ButtonType> dialog = new Dialog<>(); dialog.setTitle("Live Alert Rules");
        dialog.setHeaderText("Notify when a new tailed event matches"); dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        ListView<AppPreferences.AlertRule> list = new ListView<>(rules); list.setPrefHeight(220);
        list.setCellFactory(ignored -> new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(AppPreferences.AlertRule rule, boolean empty) { super.updateItem(rule, empty); setText(empty || rule == null ? null : rule.expression() + (rule.autoScratch() ? "  → Scratch" : "")); }
        });
        TextField expression = new TextField(); expression.setPromptText("OutOfMemoryError or regex");
        CheckBox regex = new CheckBox("Regex"); CheckBox ignoreCase = new CheckBox("Ignore case"); ignoreCase.setSelected(true);
        CheckBox scratch = new CheckBox("Automatically save matching event to Scratch"); CheckBox enabled = new CheckBox("Enabled"); enabled.setSelected(true);
        GridPane form = new GridPane(); form.setHgap(10); form.setVgap(8); form.addRow(0, new Label("Match"), expression); form.add(regex, 0, 1); form.add(ignoreCase, 1, 1); form.add(scratch, 0, 2, 2, 1); form.add(enabled, 0, 3);
        Button add = new Button("Add / Update"); Button delete = new Button("Delete");
        add.setOnAction(event -> {
            String value = expression.getText().strip(); if (value.isEmpty() || !SearchPattern.compile(value, regex.isSelected(), ignoreCase.isSelected()).valid()) return;
            AppPreferences.AlertRule rule = new AppPreferences.AlertRule(value, regex.isSelected(), ignoreCase.isSelected(), scratch.isSelected(), enabled.isSelected());
            int index = list.getSelectionModel().getSelectedIndex(); if (index >= 0) rules.set(index, rule); else rules.add(rule); list.getSelectionModel().select(rule);
        });
        delete.setOnAction(event -> { int index = list.getSelectionModel().getSelectedIndex(); if (index >= 0) rules.remove(index); });
        list.getSelectionModel().selectedItemProperty().addListener((observable, old, rule) -> { if (rule != null) { expression.setText(rule.expression()); regex.setSelected(rule.regex()); ignoreCase.setSelected(rule.ignoreCase()); scratch.setSelected(rule.autoScratch()); enabled.setSelected(rule.enabled()); } });
        dialog.getDialogPane().setContent(new VBox(10, list, form, new HBox(8, add, delete)));
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return false;
        target.clear(); target.addAll(rules); return true;
    }
}
