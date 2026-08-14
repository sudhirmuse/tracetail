/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class PayloadToolkit {
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    public String decodeJwt(String token) throws IOException { String[] parts = token.strip().split("\\."); if (parts.length < 2) throw new IllegalArgumentException("JWT must contain header and payload"); return "Header:\n" + json(new String(Base64.getUrlDecoder().decode(pad(parts[0])),StandardCharsets.UTF_8)) + "\n\nPayload (signature not verified):\n" + json(new String(Base64.getUrlDecoder().decode(pad(parts[1])),StandardCharsets.UTF_8)); }
    public String decodeBase64(String value) { return new String(Base64.getDecoder().decode(value.strip()),StandardCharsets.UTF_8); }
    public String decodeUrl(String value) { return URLDecoder.decode(value,StandardCharsets.UTF_8); }
    public String json(String value) throws IOException { return mapper.writeValueAsString(mapper.readTree(value)); }
    public String curl(String method, String url, String headers, String body) { LogRedactor redactor = new LogRedactor(); StringBuilder value = new StringBuilder("curl -X ").append(method.toUpperCase()).append(" '").append(url.replace("'","'%27'")).append("'"); headers.lines().filter(line -> line.contains(":")) .forEach(line -> value.append(" \\\n  -H '").append(redactor.redact(line).replace("'","'\\''")).append("'")); if (!body.isBlank()) value.append(" \\\n  --data-raw '").append(redactor.redact(body).replace("'","'\\''")).append("'"); return value.toString(); }
    private static String pad(String value) { return value + "=".repeat((4-value.length()%4)%4); }
}
