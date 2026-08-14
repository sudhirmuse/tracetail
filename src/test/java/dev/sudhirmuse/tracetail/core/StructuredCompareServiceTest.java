/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StructuredCompareServiceTest {
    @TempDir Path directory;

    @Test void jsonObjectOrderIsSemanticallyEquivalent() throws Exception {
        Path left = write("left.json", "{\"a\":1,\"b\":2}"); Path right = write("right.json", "{\"b\":2,\"a\":1}");
        assertTrue(new StructuredCompareService().compare(left, right, StandardCharsets.UTF_8, true, true).equivalent());
    }

    @Test void xmlAttributeOrderIsSemanticallyEquivalent() throws Exception {
        Path left = write("left.xml", "<item a=\"1\" b=\"2\"> value </item>"); Path right = write("right.xml", "<item b=\"2\" a=\"1\">value</item>");
        assertTrue(new StructuredCompareService().compare(left, right, StandardCharsets.UTF_8, true, true).equivalent());
    }

    @Test void reportsChangedText() throws Exception {
        Path left = write("left.txt", "one\ntwo"); Path right = write("right.txt", "one\nthree");
        StructuredCompareService.Result result = new StructuredCompareService().compare(left, right, StandardCharsets.UTF_8, false, false);
        assertFalse(result.equivalent()); assertTrue(result.unified().contains("two")); assertTrue(result.unified().contains("three"));
    }

    private Path write(String name, String value) throws Exception { Path path = directory.resolve(name); Files.writeString(path, value); return path; }
}
