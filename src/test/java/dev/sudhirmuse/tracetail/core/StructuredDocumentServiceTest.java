/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StructuredDocumentServiceTest {
    private final StructuredDocumentService service = new StructuredDocumentService();

    @Test void detectsJsonByContentAndFormatsIt() throws Exception {
        String source = "{\"b\":2,\"a\":1}";
        assertEquals(StructuredDocumentService.Format.JSON, service.detect(Path.of("data.unknown"), source));
        String formatted = service.format(source, StructuredDocumentService.Format.JSON);
        assertTrue(formatted.contains(System.lineSeparator()));
        assertEquals(service.canonical(source, StructuredDocumentService.Format.JSON),
            service.canonical("{\"a\":1,\"b\":2}", StructuredDocumentService.Format.JSON));
    }

    @Test void xmlFormattingPreservesRootAndRejectsDoctype() throws Exception {
        String formatted = service.format("<catalog><item id=\"1\">Book</item></catalog>", StructuredDocumentService.Format.XML);
        assertTrue(formatted.contains("<catalog>")); assertTrue(formatted.contains("<item id=\"1\">Book</item>"));
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
            () -> service.format("<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><x>&e;</x>", StructuredDocumentService.Format.XML));
    }

    @Test void formatsPropertiesAndCsv() throws Exception {
        assertEquals("a=1" + System.lineSeparator() + "z=2", service.format("z=2\na=1", StructuredDocumentService.Format.PROPERTIES));
        assertTrue(service.format("name,value\nalpha,1", StructuredDocumentService.Format.CSV).contains("name"));
    }
}
