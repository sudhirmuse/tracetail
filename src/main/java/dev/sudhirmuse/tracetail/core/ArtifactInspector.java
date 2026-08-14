/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import org.benf.cfr.reader.api.CfrDriver;
import org.benf.cfr.reader.api.OutputSinkFactory;

public final class ArtifactInspector {
    private static final int MAX_ENTRIES = 200_000;
    public String inspect(Path artifact) throws IOException {
        try (JarFile jar = new JarFile(artifact.toFile(), false)) {
            List<? extends ZipEntry> entries = jar.stream().limit(MAX_ENTRIES + 1L).toList(); if (entries.size() > MAX_ENTRIES) throw new IOException("Artifact has too many entries");
            long classes = entries.stream().filter(entry -> entry.getName().endsWith(".class")).count(); long libraries = entries.stream().filter(entry -> entry.getName().matches(".*(?:BOOT-INF|WEB-INF)/lib/.*\\.jar")).count();
            Map<Integer,Long> versions = new HashMap<>();
            for (ZipEntry entry : entries) if (entry.getName().endsWith(".class") && entry.getSize() >= 8 && entry.getSize() < 10_000_000) try (var input = jar.getInputStream(entry)) { byte[] header = input.readNBytes(8); if (header.length == 8 && ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN).getInt() == 0xCAFEBABE) versions.merge((int)ByteBuffer.wrap(header,6,2).order(ByteOrder.BIG_ENDIAN).getShort(),1L,Long::sum); }
            String manifest = jar.getManifest() == null ? "No manifest" : jar.getManifest().getMainAttributes().entrySet().stream().map(entry -> entry.getKey()+": "+entry.getValue()).sorted().collect(java.util.stream.Collectors.joining("\n"));
            StringBuilder out = new StringBuilder("JAVA ARTIFACT INSPECTOR\n\nArtifact: ").append(artifact).append("\nEntries: ").append(entries.size()).append("\nClasses: ").append(classes).append("\nEmbedded libraries: ").append(libraries).append("\nClass versions: ").append(versions).append("\n\nManifest:\n").append(manifest).append("\n\nPackages / classes:\n");
            entries.stream().map(ZipEntry::getName).filter(name -> name.endsWith(".class")).sorted().limit(2_000).forEach(name -> out.append(name).append('\n')); return out.toString();
        }
    }

    public String compare(Path left, Path right) throws IOException {
        Map<String,Long> a = entries(left), b = entries(right); List<String> names = new ArrayList<>(a.keySet()); b.keySet().stream().filter(name -> !a.containsKey(name)).forEach(names::add);
        StringBuilder out = new StringBuilder("ARTIFACT COMPARISON\n\n"); names.stream().sorted().filter(name -> !java.util.Objects.equals(a.get(name),b.get(name))).forEach(name -> out.append(a.containsKey(name) ? b.containsKey(name) ? "~ " : "- " : "+ ").append(name).append('\n')); return out.toString();
    }

    public String bytecode(Path artifact, String classEntry) throws IOException, InterruptedException {
        if (!classEntry.endsWith(".class") || classEntry.contains("..")) throw new IllegalArgumentException("Choose a valid class entry");
        Path temporary = Files.createTempDirectory("tracetail-class-");
        try (JarFile jar = new JarFile(artifact.toFile(), false)) { ZipEntry entry = jar.getEntry(classEntry); if (entry == null) throw new IOException("Class not found"); Path file = temporary.resolve(Path.of(classEntry).getFileName().toString()); try (var input = jar.getInputStream(entry)) { Files.copy(input,file); }
            Process process = new ProcessBuilder("javap","-c","-p","-verbose",file.toString()).redirectErrorStream(true).start(); String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8); if (process.waitFor()!=0) throw new IOException(output); return output;
        } finally { try (var paths = Files.walk(temporary)) { paths.sorted(Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (IOException ignored) { } }); } }
    }

    public String decompile(Path artifact, String classEntry) throws IOException {
        if (!classEntry.endsWith(".class") || classEntry.contains("..")) throw new IllegalArgumentException("Choose a valid class entry");
        Path temporary = Files.createTempDirectory("tracetail-decompile-"); StringBuilder source = new StringBuilder();
        try (JarFile jar = new JarFile(artifact.toFile(), false)) { ZipEntry entry = jar.getEntry(classEntry); if (entry == null) throw new IOException("Class not found");
            Path file = temporary.resolve(Path.of(classEntry).getFileName().toString()); try (var input = jar.getInputStream(entry)) { Files.copy(input,file); }
            OutputSinkFactory sinks = new OutputSinkFactory() {
                @Override public List<SinkClass> getSupportedSinks(SinkType type, java.util.Collection<SinkClass> available) { return available.contains(SinkClass.STRING) ? List.of(SinkClass.STRING) : List.of(); }
                @Override public <T> Sink<T> getSink(SinkType type, SinkClass sinkClass) { return value -> { if (type == SinkType.JAVA) source.append(value).append('\n'); }; }
            };
            new CfrDriver.Builder().withOutputSink(sinks).withOptions(Map.of("silent","true","showversion","false")).build().analyse(List.of(file.toString()));
            if (source.isEmpty()) throw new IOException("Decompiler produced no source"); return source.toString();
        } finally { try (var paths = Files.walk(temporary)) { paths.sorted(Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (IOException ignored) { } }); } }
    }

    private static Map<String,Long> entries(Path path) throws IOException { try (JarFile jar = new JarFile(path.toFile(),false)) { return jar.stream().limit(MAX_ENTRIES).collect(java.util.stream.Collectors.toMap(ZipEntry::getName, ZipEntry::getCrc, (a,b)->a)); } }
}
