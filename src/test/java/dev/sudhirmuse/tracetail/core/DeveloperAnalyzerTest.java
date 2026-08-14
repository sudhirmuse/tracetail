/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DeveloperAnalyzerTest {
    private final DeveloperAnalyzer analyzer = new DeveloperAnalyzer();
    @Test void groupsRootCauseAndFindsApplicationFrame(){String stack="java.lang.RuntimeException: failed\n at org.springframework.Run.go(Run.java:1)\nCaused by: com.example.PaymentException: declined\n at com.acme.Payments.charge(Payments.java:42)";String report=analyzer.stackTraces(List.of(event(stack,"trace-1")));assertTrue(report.contains("PaymentException"));assertTrue(report.contains("com.acme.Payments.charge"));}
    @Test void correlatesAndCalculatesPerformance(){List<DiagnosticEvent> events=List.of(event("request took 120ms","trace-1"),event("request completed in 2s","trace-1"));assertTrue(analyzer.correlations(events).contains("2 events"));assertTrue(analyzer.performance(events).contains("2000.0 ms"));}
    @Test void analyzesThreadAndGcText(){assertTrue(analyzer.threadDump("\"worker\"\n java.lang.Thread.State: BLOCKED\nFound one Java-level deadlock").contains("BLOCKED"));assertTrue(analyzer.gc("GC(1) Pause Young 245.0ms").contains("245.0"));}
    private static DiagnosticEvent event(String text,String trace){LogEvent event=new LogEvent(1,1,Instant.now(),LogLevel.INFO,"worker",trace,text.lines().findFirst().orElse(""),text);return new DiagnosticEvent(Path.of("service.log"),Instant.now(),event);}
}
