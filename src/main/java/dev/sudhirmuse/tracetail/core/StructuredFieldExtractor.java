/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StructuredFieldExtractor {
    private static final int MAX_FIELDS = 32;
    private static final Pattern PAIR = Pattern.compile("(?:^|\\s)([A-Za-z_][A-Za-z0-9_.-]{0,63})=(\"[^\"]*\"|[^\\s]+)");
    private final ObjectMapper mapper = new ObjectMapper();

    public Map<String, String> extract(String text) {
        String value = text == null ? "" : text.strip();
        if (value.startsWith("{") && value.endsWith("}")) {
            try {
                JsonNode root = mapper.readTree(value);
                Map<String, String> fields = new LinkedHashMap<>();
                root.properties().forEach(entry -> { if (fields.size() < MAX_FIELDS && entry.getValue().isValueNode()) fields.put(entry.getKey(), entry.getValue().asText()); });
                return Map.copyOf(fields);
            } catch (Exception ignored) { /* fall through to key=value extraction */ }
        }
        Map<String, String> fields = new LinkedHashMap<>();
        Matcher matcher = PAIR.matcher(value);
        while (matcher.find() && fields.size() < MAX_FIELDS) {
            String field = matcher.group(2);
            if (field.length() >= 2 && field.startsWith("\"") && field.endsWith("\"")) field = field.substring(1, field.length() - 1);
            fields.put(matcher.group(1), field);
        }
        return Map.copyOf(fields);
    }
}
