/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail;

import dev.sudhirmuse.tracetail.core.RemoteSourceService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class RemoteSourceDialog {
    public enum Type { HTTP, SSH, DOCKER, KUBERNETES, CLOUDWATCH, AZURE_MONITOR, GCP_LOGGING }

    public record Request(Type type, String location, String host, int port, String user, String keyFile,
                          String namespace, String container, String context, boolean follow, boolean previous) { }

    private RemoteSourceDialog() { }

    static Optional<Request> show(Stage owner, String theme) {
        Dialog<Request> dialog = new Dialog<>(); dialog.initOwner(owner); dialog.setTitle("Open Remote Log");
        ButtonType open = new ButtonType("Open", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(open, ButtonType.CANCEL);

        ComboBox<Type> type = new ComboBox<>(); type.getItems().setAll(Type.values()); type.setValue(Type.HTTP);
        type.setConverter(new StringConverter<>() {
            @Override public String toString(Type value) { return value == null ? "" : switch (value) {
                case HTTP -> "HTTP / HTTPS"; case SSH -> "SSH / SFTP"; case DOCKER -> "Docker"; case KUBERNETES -> "Kubernetes";
                case CLOUDWATCH -> "AWS CloudWatch"; case AZURE_MONITOR -> "Azure Monitor"; case GCP_LOGGING -> "GCP Logging";
            }; }
            @Override public Type fromString(String value) { return Type.valueOf(value); }
        });
        ComboBox<String> location = editableCombo(); ComboBox<String> namespace = editableCombo();
        ComboBox<String> container = editableCombo(); ComboBox<String> context = editableCombo();
        namespace.getEditor().setText("default");
        TextField host = new TextField(); TextField port = new TextField("22"); port.setPrefColumnCount(6);
        TextField user = new TextField(); TextField key = new TextField();
        CheckBox follow = new CheckBox("Follow live output"); follow.setSelected(true);
        CheckBox previous = new CheckBox("Previous terminated container");
        Button keyBrowse = new Button("Key…"); Button discover = new Button("Browse / Discover");
        Label locationLabel = new Label(); Label status = new Label("Remote credentials are never saved by TraceTail.");

        keyBrowse.setOnAction(event -> {
            FileChooser chooser = new FileChooser(); chooser.setTitle("Choose SSH private key");
            java.io.File selected = chooser.showOpenDialog(owner); if (selected != null) key.setText(selected.getAbsolutePath());
        });

        GridPane form = new GridPane(); form.setHgap(10); form.setVgap(8); form.setPadding(new Insets(12));
        form.addRow(0, new Label("Source"), type);
        form.addRow(1, locationLabel, location, discover);
        form.addRow(2, new Label("Host / account"), host);
        form.addRow(3, new Label("SSH port"), port);
        form.addRow(4, new Label("Username"), user);
        form.addRow(5, new Label("SSH key"), key, keyBrowse);
        form.addRow(6, new Label("Context"), context);
        form.addRow(7, new Label("Namespace"), namespace);
        form.addRow(8, new Label("Container"), container);
        form.add(follow, 1, 9); form.add(previous, 2, 9); form.add(status, 1, 10, 2, 1);
        location.setPrefWidth(410);

        Runnable update = () -> {
            Type selected = type.getValue();
            locationLabel.setText(switch (selected) {
                case HTTP -> "URL"; case SSH -> "Remote file"; case DOCKER -> "Container"; case KUBERNETES -> "Pod";
                case CLOUDWATCH -> "Log group"; case AZURE_MONITOR -> "KQL query"; case GCP_LOGGING -> "Log filter";
            });
            host.setPromptText(switch (selected) {
                case SSH -> "server.example.com"; case DOCKER -> "Docker host (optional)"; case CLOUDWATCH -> "AWS region";
                case AZURE_MONITOR -> "Workspace ID"; case GCP_LOGGING -> "GCP project"; default -> "";
            });
            boolean ssh = selected == Type.SSH, kube = selected == Type.KUBERNETES;
            boolean hostEnabled = ssh || selected == Type.DOCKER || selected == Type.CLOUDWATCH
                || selected == Type.AZURE_MONITOR || selected == Type.GCP_LOGGING;
            host.setDisable(!hostEnabled); port.setDisable(!ssh); user.setDisable(!ssh); key.setDisable(!ssh); keyBrowse.setDisable(!ssh);
            namespace.setDisable(!kube); container.setDisable(!kube); context.setDisable(!kube); previous.setDisable(!kube);
            discover.setDisable(selected == Type.HTTP || selected == Type.CLOUDWATCH || selected == Type.AZURE_MONITOR || selected == Type.GCP_LOGGING);
            follow.setDisable(selected == Type.HTTP || selected == Type.AZURE_MONITOR || previous.isSelected());
            if (selected == Type.HTTP || selected == Type.AZURE_MONITOR || previous.isSelected()) follow.setSelected(false);
        };
        type.valueProperty().addListener((observable, old, value) -> update.run());
        previous.selectedProperty().addListener((observable, old, value) -> update.run()); update.run();

        java.util.function.Supplier<Request> request = () -> new Request(type.getValue(), value(location), host.getText().strip(),
            parsePort(port.getText()), user.getText().strip(), key.getText().strip(), value(namespace), value(container), value(context),
            follow.isSelected(), previous.isSelected());
        discover.setOnAction(event -> {
            discover.setDisable(true); status.setText("Discovering remote resources…");
            CompletableFuture.supplyAsync(() -> {
                try { return new RemoteSourceService().discover(request.get()); }
                catch (IOException exception) { throw new java.util.concurrent.CompletionException(exception); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new java.util.concurrent.CompletionException(exception); }
            }).whenComplete((found, failure) -> Platform.runLater(() -> {
                discover.setDisable(false);
                if (failure != null) { Throwable cause = failure.getCause() == null ? failure : failure.getCause(); status.setText("Discovery failed: " + cause.getMessage()); return; }
                apply(location, found.locations()); apply(context, found.contexts()); apply(namespace, found.namespaces()); apply(container, found.containers());
                status.setText("Discovered " + found.total() + " remote item(s). Select values, then Open.");
            }));
        });

        dialog.getDialogPane().setContent(form);
        dialog.setOnShown(event -> ThemeSupport.apply(dialog.getDialogPane().getScene(), theme));
        dialog.setResultConverter(button -> button == open ? request.get() : null);
        return dialog.showAndWait().filter(value -> !value.location().isBlank());
    }

    private static ComboBox<String> editableCombo() { ComboBox<String> combo = new ComboBox<>(); combo.setEditable(true); combo.setPrefWidth(300); return combo; }
    private static String value(ComboBox<String> combo) { return combo.getEditor().getText().strip(); }
    private static int parsePort(String value) { try { int parsed = Integer.parseInt(value.strip()); return parsed > 0 && parsed <= 65535 ? parsed : 22; } catch (NumberFormatException ignored) { return 22; } }
    private static void apply(ComboBox<String> combo, List<String> values) {
        String current = value(combo); combo.getItems().setAll(values);
        if (!current.isBlank()) combo.getEditor().setText(current); else if (!values.isEmpty()) combo.getSelectionModel().selectFirst();
    }
}
