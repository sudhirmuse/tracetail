/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import dev.sudhirmuse.tracetail.RemoteSourceDialog;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class RemoteSourceService {
    public record Opened(Path spool, Process process) { }
    public record Discovery(List<String> locations, List<String> contexts, List<String> namespaces, List<String> containers) {
        public int total() { return locations.size() + contexts.size() + namespaces.size() + containers.size(); }
    }

    public Opened open(RemoteSourceDialog.Request request, Path directory) throws IOException, InterruptedException {
        Files.createDirectories(directory);
        String safe = safeName(request.location());
        String suffix = request.type() == RemoteSourceDialog.Type.HTTP ? httpSuffix(request.location()) : ".log";
        Path spool = unique(directory, safe + suffix);
        if (request.type() == RemoteSourceDialog.Type.HTTP) {
            download(request.location(), spool);
            return new Opened(spool, null);
        }
        Files.writeString(spool, "", StandardOpenOption.CREATE_NEW);
        ProcessBuilder builder = new ProcessBuilder(command(request));
        builder.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(spool.toFile()));
        return new Opened(spool, builder.start());
    }

    List<String> command(RemoteSourceDialog.Request request) {
        return switch (request.type()) {
            case SSH -> ssh(request);
            case DOCKER -> docker(request);
            case KUBERNETES -> kubernetes(request);
            case CLOUDWATCH -> cloudWatch(request);
            case AZURE_MONITOR -> azureMonitor(request);
            case GCP_LOGGING -> gcpLogging(request);
            case HTTP -> throw new IllegalArgumentException("HTTP does not use a command");
        };
    }

    public Discovery discover(RemoteSourceDialog.Request request) throws IOException, InterruptedException {
        return switch (request.type()) {
            case SSH -> new Discovery(run(sshBrowse(request)), List.of(), List.of(), List.of());
            case DOCKER -> new Discovery(run(dockerList(request)), List.of(), List.of(), List.of());
            case KUBERNETES -> discoverKubernetes(request);
            default -> new Discovery(List.of(), List.of(), List.of(), List.of());
        };
    }

    private static List<String> docker(RemoteSourceDialog.Request request) {
        requireSafe(request.location(), "container");
        List<String> command = new ArrayList<>(List.of("docker"));
        if (!request.host().isBlank()) command.addAll(List.of("--host", request.host()));
        command.addAll(List.of("logs", "--tail", "10000"));
        if (request.follow()) command.add("--follow");
        command.add(request.location());
        return command;
    }

    private static List<String> dockerList(RemoteSourceDialog.Request request) {
        List<String> command = new ArrayList<>(List.of("docker"));
        if (!request.host().isBlank()) command.addAll(List.of("--host", request.host()));
        command.addAll(List.of("ps", "--format", "{{.Names}}"));
        return command;
    }

    private static List<String> ssh(RemoteSourceDialog.Request request) {
        requireSafe(request.host(), "host"); requireSafe(request.user(), "username");
        if (request.location().contains("\n") || request.location().contains("\r") || request.location().contains("'"))
            throw new IllegalArgumentException("Remote path contains unsupported characters");
        List<String> command = sshBase(request);
        String quoted = "'" + request.location() + "'";
        command.add(request.follow() ? "tail -n 10000 -F -- " + quoted : "tail -n 10000 -- " + quoted);
        return command;
    }

    private static List<String> sshBrowse(RemoteSourceDialog.Request request) {
        String location = request.location().isBlank() ? "/var/log" : request.location();
        if (location.contains("\n") || location.contains("\r") || location.contains("'"))
            throw new IllegalArgumentException("Remote path contains unsupported characters");
        int slash = location.lastIndexOf('/');
        String directory = location.endsWith("/") ? location : slash < 0 ? "." : location.substring(0, slash + 1);
        List<String> command = sshBase(request);
        command.add("find '" + directory + "' -maxdepth 2 -type f -print 2>/dev/null | head -n 500");
        return command;
    }

    private static List<String> sshBase(RemoteSourceDialog.Request request) {
        requireSafe(request.host(), "host"); requireSafe(request.user(), "username");
        List<String> command = new ArrayList<>(List.of("ssh", "-o", "BatchMode=yes", "-p", Integer.toString(request.port())));
        if (!request.keyFile().isBlank()) command.addAll(List.of("-i", request.keyFile()));
        command.add(request.user().isBlank() ? request.host() : request.user() + "@" + request.host());
        return command;
    }

    private static List<String> kubernetes(RemoteSourceDialog.Request request) {
        requireSafe(request.location(), "pod");
        List<String> command = new ArrayList<>(List.of("kubectl"));
        if (!request.context().isBlank()) command.addAll(List.of("--context", request.context()));
        command.addAll(List.of("logs", request.location(), "--namespace", request.namespace().isBlank() ? "default" : request.namespace(), "--tail=10000"));
        if (!request.container().isBlank()) command.addAll(List.of("--container", request.container()));
        if (request.previous()) command.add("--previous");
        else if (request.follow()) command.add("--follow");
        return command;
    }

    private static Discovery discoverKubernetes(RemoteSourceDialog.Request request) throws IOException, InterruptedException {
        List<String> contexts = run(List.of("kubectl", "config", "get-contexts", "-o", "name"));
        String selectedContext = request.context().isBlank() && !contexts.isEmpty() ? contexts.getFirst() : request.context();
        List<String> prefix = new ArrayList<>(List.of("kubectl"));
        if (!selectedContext.isBlank()) prefix.addAll(List.of("--context", selectedContext));
        List<String> namespaceCommand = new ArrayList<>(prefix); namespaceCommand.addAll(List.of("get", "namespaces", "-o", "jsonpath={range .items[*]}{.metadata.name}{'\\n'}{end}"));
        List<String> namespaces = run(namespaceCommand);
        String selectedNamespace = request.namespace().isBlank() ? "default" : request.namespace();
        List<String> podCommand = new ArrayList<>(prefix); podCommand.addAll(List.of("get", "pods", "--namespace", selectedNamespace, "-o", "jsonpath={range .items[*]}{.metadata.name}{'\\n'}{end}"));
        List<String> pods = run(podCommand);
        String selectedPod = request.location().isBlank() && !pods.isEmpty() ? pods.getFirst() : request.location();
        List<String> containers = List.of();
        if (!selectedPod.isBlank()) {
            List<String> containerCommand = new ArrayList<>(prefix); containerCommand.addAll(List.of("get", "pod", selectedPod, "--namespace", selectedNamespace,
                "-o", "jsonpath={range .spec.containers[*]}{.name}{'\\n'}{end}"));
            containers = run(containerCommand);
        }
        return new Discovery(pods, contexts, namespaces, containers);
    }

    private static List<String> cloudWatch(RemoteSourceDialog.Request request) {
        List<String> command = new ArrayList<>(List.of("aws", "logs", "tail", request.location(), "--format", "detailed"));
        if (!request.host().isBlank()) command.addAll(List.of("--region", request.host()));
        if (request.follow()) command.add("--follow");
        return command;
    }

    private static List<String> azureMonitor(RemoteSourceDialog.Request request) {
        if (request.host().isBlank()) throw new IllegalArgumentException("Azure workspace ID is required");
        return List.of("az", "monitor", "log-analytics", "query", "--workspace", request.host(), "--analytics-query", request.location(), "--output", "json");
    }

    private static List<String> gcpLogging(RemoteSourceDialog.Request request) {
        List<String> command = new ArrayList<>(List.of("gcloud", "logging", request.follow() ? "tail" : "read", request.location()));
        if (!request.host().isBlank()) command.addAll(List.of("--project", request.host()));
        if (!request.follow()) command.addAll(List.of("--limit", "10000"));
        command.addAll(List.of("--format", "json"));
        return command;
    }

    private static void download(String value, Path destination) throws IOException, InterruptedException {
        URI uri = URI.create(value);
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme()))
            throw new IllegalArgumentException("Only HTTP and HTTPS URLs are supported");
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(20)).build();
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(10)).GET().build();
        HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(destination));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            Files.deleteIfExists(destination);
            throw new IOException("Remote server returned HTTP " + response.statusCode());
        }
    }

    private static Path unique(Path directory, String name) {
        String timestamp = Long.toString(System.currentTimeMillis());
        return directory.resolve(timestamp + "-" + name);
    }

    private static String httpSuffix(String value) {
        String path = URI.create(value).getPath().toLowerCase(Locale.ROOT);
        if (path.endsWith(".gz")) return ".gz";
        if (path.endsWith(".zip")) return ".zip";
        return ".log";
    }

    private static String safeName(String value) {
        String text = value.replaceAll("[^A-Za-z0-9._-]", "_");
        if (text.length() > 80) text = text.substring(text.length() - 80);
        return text.isBlank() ? "remote" : text.toLowerCase(Locale.ROOT);
    }

    private static void requireSafe(String value, String label) {
        if (!value.matches("[A-Za-z0-9._:@-]*")) throw new IllegalArgumentException("Invalid " + label);
    }

    private static List<String> run(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        if (!process.waitFor(20, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IOException("Remote discovery timed out"); }
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).strip();
        if (process.exitValue() != 0) throw new IOException(output.isBlank() ? "Remote command failed" : output);
        if (output.isBlank()) return List.of();
        return output.lines().map(String::strip).filter(value -> !value.isBlank()).distinct().limit(500).toList();
    }
}
