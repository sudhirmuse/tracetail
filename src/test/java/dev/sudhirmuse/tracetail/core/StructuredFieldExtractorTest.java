/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StructuredFieldExtractorTest {
    private final StructuredFieldExtractor extractor = new StructuredFieldExtractor();
    @Test void extractsJsonScalars() { assertEquals("500", extractor.extract("{\"status\":500,\"ok\":false}").get("status")); }
    @Test void extractsLogfmtPairs() { assertEquals("checkout failed", extractor.extract("level=error message=\"checkout failed\"").get("message")); }
}
