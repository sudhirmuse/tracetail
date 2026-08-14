/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DeveloperUtilitiesTest {
    @TempDir Path directory;
    @Test void payloadToolkitDecodesJwtAndRedactsCurl()throws Exception{PayloadToolkit toolkit=new PayloadToolkit();String token="eyJhbGciOiJub25lIn0.eyJzdWIiOiJzdWRoaXIifQ.";assertTrue(toolkit.decodeJwt(token).contains("sudhir"));String curl=toolkit.curl("post","https://example/api","Authorization: Bearer secret","{\"password\":\"hunter2\"}");assertFalse(curl.contains("hunter2"));assertFalse(curl.contains("secret"));}
    @Test void parserProfileTemplateRoundTrips()throws Exception{Path path=directory.resolve("profile.yaml");ParserProfileStore store=new ParserProfileStore();store.saveTemplate(path);ParserProfile profile=store.load(path);assertTrue(store.test(profile,"2026 INFO [worker] traceId=abcd-1234").contains("INFO"));}
    @Test void configurationComparisonMasksSecrets()throws Exception{Path left=directory.resolve("left.properties"),right=directory.resolve("right.properties");Files.writeString(left,"server.port=8080\npassword=secret");Files.writeString(right,"server.port=9090\npassword=other");String report=new ConfigurationAnalyzer().compare(left,right);assertTrue(report.contains("server.port"));assertFalse(report.contains("secret"));assertFalse(report.contains("other"));}
}
