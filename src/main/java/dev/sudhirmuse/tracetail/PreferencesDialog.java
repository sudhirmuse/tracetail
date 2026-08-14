/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail;

import dev.sudhirmuse.tracetail.settings.AppPreferences;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;


final class PreferencesDialog {
    private PreferencesDialog() { }

    static boolean show(AppPreferences preferences) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("TraceTail Preferences");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        CheckBox wrap = new CheckBox("Wrap long lines");
        wrap.setSelected(preferences.wrapLines);
        CheckBox top = new CheckBox("Always on top");
        top.setSelected(preferences.alwaysOnTop);
        CheckBox reopen = new CheckBox("Reopen recent files at startup");
        reopen.setSelected(preferences.reopenRecentFiles);
        Spinner<Double> font = new Spinner<>(8, 32, preferences.fontSize, 1);
        font.setEditable(true);
        Spinner<Integer> tabs = new Spinner<>(1, 16, preferences.tabWidth);
        ComboBox<String> side = new ComboBox<>();
        side.getItems().setAll("TOP", "RIGHT", "BOTTOM", "LEFT");
        side.setValue(preferences.tabSide);
        ComboBox<String> charset = new ComboBox<>();
        charset.getItems().setAll("UTF-8", "windows-1252", "ISO-8859-1", "UTF-16LE", "UTF-16BE");
        charset.setValue(preferences.charset);
        ComboBox<String> theme = new ComboBox<>();
        theme.getItems().setAll("DARK", "LIGHT", "SYSTEM");
        theme.setValue(preferences.theme == null ? "DARK" : preferences.theme);
        Button rules = new Button("Manage highlight rules…");
        rules.setMaxWidth(Double.MAX_VALUE);
        rules.setOnAction(event -> HighlightRulesDialog.show(preferences.highlightRules));

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(9); grid.setPadding(new Insets(12));
        grid.add(wrap, 0, 0, 2, 1); grid.add(top, 0, 1, 2, 1); grid.add(reopen, 0, 2, 2, 1);
        grid.addRow(3, new Label("Font size"), font);
        grid.addRow(4, new Label("TAB width"), tabs);
        grid.addRow(5, new Label("Tab position"), side);
        grid.addRow(6, new Label("Text encoding"), charset);
        grid.addRow(7, new Label("Theme"), theme);
        grid.addRow(8, new Label("Highlighting"), rules);
        dialog.getDialogPane().setContent(grid);
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return false;
        preferences.wrapLines = wrap.isSelected();
        preferences.alwaysOnTop = top.isSelected();
        preferences.reopenRecentFiles = reopen.isSelected();
        preferences.fontSize = font.getValue();
        preferences.tabWidth = tabs.getValue();
        preferences.tabSide = side.getValue();
        preferences.charset = charset.getValue();
        preferences.theme = theme.getValue();
        return true;
    }
}
