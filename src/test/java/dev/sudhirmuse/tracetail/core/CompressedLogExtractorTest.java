/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CompressedLogExtractorTest {
    @Test void extractsGzipAndSupportedZipEntries(@TempDir Path directory) throws Exception {
        Path gzip = directory.resolve("app.log.gz");
        try (GZIPOutputStream out = new GZIPOutputStream(Files.newOutputStream(gzip))) { out.write("ERROR gzip\n".getBytes()); }
        assertEquals("ERROR gzip\n", Files.readString(new CompressedLogExtractor().extract(gzip, directory.resolve("out1")).getFirst()));
        Path zip = directory.resolve("logs.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("nested/app.log")); out.write("INFO zip\n".getBytes()); out.closeEntry();
        }
        assertEquals("INFO zip\n", Files.readString(new CompressedLogExtractor().extract(zip, directory.resolve("out2")).getFirst()));
    }
}
