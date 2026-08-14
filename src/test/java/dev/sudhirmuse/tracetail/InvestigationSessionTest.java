/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class InvestigationSessionTest {
    @Test void roundTripsNamedFileSet() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        InvestigationSession session = new InvestigationSession("incident-42", "2026-08-15T00:00:00Z", List.of("a.log", "b.log"));
        InvestigationSession restored = mapper.readValue(mapper.writeValueAsBytes(session), InvestigationSession.class);
        assertEquals(session, restored);
    }
}
