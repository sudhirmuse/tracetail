/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail;

import dev.sudhirmuse.tracetail.core.ArtifactInspector;
import dev.sudhirmuse.tracetail.core.ConfigurationAnalyzer;
import dev.sudhirmuse.tracetail.core.DeveloperAnalyzer;
import dev.sudhirmuse.tracetail.core.DiagnosticEvent;
import dev.sudhirmuse.tracetail.core.IncidentPackageExporter;
import dev.sudhirmuse.tracetail.core.ParserProfileStore;
import dev.sudhirmuse.tracetail.core.PayloadToolkit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class DeveloperToolsDialog {
    private DeveloperToolsDialog() { }

    static void show(Stage owner, List<DiagnosticEvent> events, Path settingsDirectory, String theme, Consumer<String> status) {
        Stage stage = new Stage(); stage.initOwner(owner); stage.setTitle("Developer Diagnostics");
        TextArea report = new TextArea("Choose an analyzer. Expensive work runs on demand and never blocks Fast View."); report.setEditable(false); report.setWrapText(false); report.getStyleClass().add("details");
        Label state = new Label(events.size() + " bounded event(s) available"); DeveloperAnalyzer analyzer = new DeveloperAnalyzer();
        Button stack = button("Stack Traces", () -> report.setText(analyzer.stackTraces(events)));
        Button correlate = button("Correlation", () -> report.setText(analyzer.correlations(events)));
        Button performance = button("Performance", () -> report.setText(analyzer.performance(events)));
        Button quality = button("Log Quality", () -> report.setText(analyzer.quality(events)));
        Button thread = button("Thread Dump…", () -> chooseText(owner,"Choose JVM thread dump",path -> async(report,state,() -> analyzer.threadDump(readBounded(path)))));
        Button gc = button("GC Log…", () -> chooseText(owner,"Choose GC log",path -> async(report,state,() -> analyzer.gc(readBounded(path)))));
        Button config = button("Compare Config…", () -> choosePair(owner,"configuration",(left,right) -> async(report,state,() -> new ConfigurationAnalyzer().compare(left,right))));
        Button artifact = button("Inspect Artifact…", () -> chooseArtifact(owner,path -> async(report,state,() -> new ArtifactInspector().inspect(path))));
        Button artifactCompare = button("Compare Artifacts…", () -> choosePair(owner,"Java artifact",(left,right) -> async(report,state,() -> new ArtifactInspector().compare(left,right))));
        Button bytecode = button("Decompile Class…", () -> chooseArtifact(owner,path -> new TextInputDialog("com/example/Application.class").showAndWait().ifPresent(entry -> async(report,state,() -> new ArtifactInspector().decompile(path,entry)))));
        Button payload = button("Payload Tools…", () -> payload(owner,report));
        Button parser = button("Parser Profile…", () -> parserProfile(owner,settingsDirectory,report,state));
        Button incident = button("Export Incident…", () -> exportIncident(owner,events,status));
        ToolBar first = new ToolBar(stack,correlate,performance,quality,thread,gc,config);
        ToolBar second = new ToolBar(artifact,artifactCompare,bytecode,payload,parser,incident,state);
        BorderPane root = new BorderPane(report); root.setTop(new javafx.scene.layout.VBox(first,second)); Scene scene = new Scene(root,1300,800); ThemeSupport.apply(scene,theme);stage.setScene(scene);stage.show();
    }

    private static Button button(String name,Runnable action){Button button=new Button(name);button.setOnAction(event->action.run());return button;}
    private static void async(TextArea report,Label state,ThrowingSupplier task){state.setText("Working…");CompletableFuture.supplyAsync(()->{try{return task.get();}catch(Exception exception){throw new java.util.concurrent.CompletionException(exception);}}).whenComplete((value,failure)->Platform.runLater(()->{if(failure==null){report.setText(value);state.setText("Ready");}else{Throwable cause=failure.getCause()==null?failure:failure.getCause();state.setText("Failed");new Alert(Alert.AlertType.ERROR,cause.getMessage(),ButtonType.OK).showAndWait();}}));}
    private static String readBounded(Path path)throws IOException{long max=64L*1024*1024;if(Files.size(path)>max)throw new IOException("Diagnostic text input is limited to 64 MiB");return Files.readString(path);}
    private static void chooseText(Stage owner,String title,Consumer<Path> selected){FileChooser chooser=new FileChooser();chooser.setTitle(title);java.io.File file=chooser.showOpenDialog(owner);if(file!=null)selected.accept(file.toPath());}
    private static void chooseArtifact(Stage owner,Consumer<Path> selected){FileChooser chooser=new FileChooser();chooser.setTitle("Choose JAR/WAR/EAR");chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Java artifacts","*.jar","*.war","*.ear"));java.io.File file=chooser.showOpenDialog(owner);if(file!=null)selected.accept(file.toPath());}
    private static void choosePair(Stage owner,String label,PairConsumer selected){FileChooser chooser=new FileChooser();chooser.setTitle("Choose left "+label);java.io.File left=chooser.showOpenDialog(owner);if(left==null)return;chooser.setTitle("Choose right "+label);java.io.File right=chooser.showOpenDialog(owner);if(right!=null)selected.accept(left.toPath(),right.toPath());}
    private static void exportIncident(Stage owner,List<DiagnosticEvent> events,Consumer<String> status){FileChooser chooser=new FileChooser();chooser.setTitle("Export sanitized incident package");chooser.setInitialFileName("tracetail-incident.zip");chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ZIP package","*.zip"));java.io.File file=chooser.showSaveDialog(owner);if(file==null)return;try{new IncidentPackageExporter().export(file.toPath(),events);status.accept("Exported sanitized incident package: "+file);}catch(IOException exception){new Alert(Alert.AlertType.ERROR,exception.getMessage(),ButtonType.OK).showAndWait();}}
    private static void payload(Stage owner,TextArea report){ChoiceDialog<String> choice=new ChoiceDialog<>("Decode JWT",List.of("Decode JWT","Decode Base64","Decode URL","Format JSON","Build sanitized curl"));choice.setTitle("Payload Tools");choice.showAndWait().ifPresent(tool->{PayloadToolkit kit=new PayloadToolkit();try{if(tool.equals("Build sanitized curl")){TextInputDialog url=new TextInputDialog("https://example/api");url.setHeaderText("Request URL");var selected=url.showAndWait();if(selected.isEmpty())return;TextInputDialog body=new TextInputDialog("{}");body.setHeaderText("Request body (secrets will be redacted)");report.setText(kit.curl("POST",selected.get(),"Content-Type: application/json",body.showAndWait().orElse("")));return;}TextInputDialog input=new TextInputDialog();input.setHeaderText(tool);String value=input.showAndWait().orElse(null);if(value==null)return;report.setText(switch(tool){case "Decode JWT"->kit.decodeJwt(value);case "Decode Base64"->kit.decodeBase64(value);case "Decode URL"->kit.decodeUrl(value);default->kit.json(value);});}catch(Exception exception){new Alert(Alert.AlertType.ERROR,exception.getMessage(),ButtonType.OK).showAndWait();}});}
    private static void parserProfile(Stage owner,Path settings,TextArea report,Label state){ChoiceDialog<String> choice=new ChoiceDialog<>("Create template",List.of("Create template","Load and test profile"));choice.showAndWait().ifPresent(action->{if(action.equals("Create template")){Path path=settings.resolve("parsers").resolve("custom-parser.yaml");try{new ParserProfileStore().saveTemplate(path);report.setText("Parser profile template created:\n"+path+"\n\nEdit it to define timestamp, severity, thread, trace, and custom columns.");}catch(IOException exception){new Alert(Alert.AlertType.ERROR,exception.getMessage(),ButtonType.OK).showAndWait();}}else{FileChooser chooser=new FileChooser();chooser.setTitle("Choose parser profile");java.io.File file=chooser.showOpenDialog(owner);if(file==null)return;TextInputDialog line=new TextInputDialog();line.setHeaderText("Sample log line");line.showAndWait().ifPresent(value->async(report,state,()->{ParserProfileStore store=new ParserProfileStore();return store.test(store.load(file.toPath()),value);}));}});}
    @FunctionalInterface private interface ThrowingSupplier{String get()throws Exception;}
    @FunctionalInterface private interface PairConsumer{void accept(Path left,Path right);}
}
