/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class CompressedLogExtractor {
    public List<Path> extract(Path archive, Path destinationRoot) throws IOException {
        Path destination = destinationRoot.resolve(UUID.randomUUID().toString()); Files.createDirectories(destination);
        String name = archive.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".gz")) return List.of(extractGzip(archive, destination));
        if (name.endsWith(".zip")) return extractZip(archive, destination);
        throw new IOException("Unsupported compressed format: " + archive.getFileName());
    }

    private Path extractGzip(Path archive, Path destination) throws IOException {
        String name = archive.getFileName().toString(); name = name.substring(0, name.length() - 3);
        Path output = destination.resolve(safeName(name));
        try (InputStream input = new GZIPInputStream(Files.newInputStream(archive))) { Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING); }
        return output;
    }

    private List<Path> extractZip(Path archive, Path destination) throws IOException {
        List<Path> extracted = new ArrayList<>();
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (entry.isDirectory() || !supported(entry.getName())) continue;
                Path output = destination.resolve(safeName(Path.of(entry.getName()).getFileName().toString())).normalize();
                if (!output.startsWith(destination)) throw new IOException("Unsafe ZIP entry: " + entry.getName());
                Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING); extracted.add(output);
            }
        }
        if (extracted.isEmpty()) throw new IOException("Archive contains no supported log files");
        return List.copyOf(extracted);
    }

    private boolean supported(String name) {
        String value = name.toLowerCase(Locale.ROOT);
        return value.endsWith(".log") || value.endsWith(".txt") || value.endsWith(".out") || value.endsWith(".json") || value.endsWith(".csv");
    }
    private String safeName(String value) { return value.replaceAll("[^A-Za-z0-9._-]", "_"); }
}
