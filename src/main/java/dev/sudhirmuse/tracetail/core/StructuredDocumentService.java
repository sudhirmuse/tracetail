/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import org.xml.sax.InputSource;

public final class StructuredDocumentService {
    public static final long MAX_STRUCTURED_BYTES = 32L * 1024 * 1024;
    private static final Pattern SQL_KEYWORDS = Pattern.compile("(?i)\\s+\\b(SELECT|FROM|WHERE|LEFT JOIN|RIGHT JOIN|INNER JOIN|OUTER JOIN|JOIN|GROUP BY|ORDER BY|HAVING|UNION|VALUES|SET)\\b\\s+");
    private final ObjectMapper json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final YAMLMapper yaml = YAMLMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    public enum Format { JSON, XML, YAML, PROPERTIES, CSV, SQL, MARKDOWN, TEXT }
    public record Document(Path path, Format format, String raw, String formatted, DocumentNode tree) { }
    public record DocumentNode(String name, String value, List<DocumentNode> children) { }

    public Document load(Path path, Charset charset) throws IOException {
        long size = Files.size(path);
        if (size > MAX_STRUCTURED_BYTES) throw new IOException("Structured View is limited to 32 MiB; use Fast View for this " + humanSize(size) + " file");
        String raw = Files.readString(path, charset);
        Format format = detect(path, raw);
        String formatted = format(raw, format);
        DocumentNode tree = tree(raw, format);
        return new Document(path, format, raw, formatted, tree);
    }

    public Format detect(Path path, String content) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        String stripped = content.stripLeading();
        if (name.endsWith(".json") || stripped.startsWith("{") || stripped.startsWith("[")) return Format.JSON;
        if (name.endsWith(".xml") || stripped.startsWith("<?xml") || stripped.matches("(?s)^<[A-Za-z_:][^>]*>.*")) return Format.XML;
        if (name.endsWith(".yaml") || name.endsWith(".yml")) return Format.YAML;
        if (name.endsWith(".properties") || name.endsWith(".ini") || name.endsWith(".conf")) return Format.PROPERTIES;
        if (name.endsWith(".csv") || name.endsWith(".tsv")) return Format.CSV;
        if (name.endsWith(".sql")) return Format.SQL;
        if (name.endsWith(".md") || name.endsWith(".markdown")) return Format.MARKDOWN;
        if (stripped.matches("(?is)^(SELECT|INSERT|UPDATE|DELETE|CREATE|ALTER|WITH)\\b.*")) return Format.SQL;
        if (stripped.matches("(?s)^#{1,6}\\s+.*")) return Format.MARKDOWN;
        List<String> sampleLines = content.lines().filter(line -> !line.isBlank()).limit(4).toList();
        if (sampleLines.size() >= 2 && sampleLines.stream().allMatch(line -> line.contains(","))) return Format.CSV;
        if (!sampleLines.isEmpty() && sampleLines.stream().allMatch(line -> line.matches("\\s*[A-Za-z0-9_.-]+\\s*[=:].*")))
            return sampleLines.stream().anyMatch(line -> line.contains("=")) ? Format.PROPERTIES : Format.YAML;
        return Format.TEXT;
    }

    public String format(String content, Format format) throws IOException {
        return switch (format) {
            case JSON -> pretty(json, content);
            case XML -> xml(content, true);
            case YAML -> pretty(yaml, content);
            case PROPERTIES -> properties(content);
            case CSV -> csv(content);
            case SQL -> sql(content);
            case MARKDOWN, TEXT -> content;
        };
    }

    public String canonical(String content, Format format) throws IOException {
        return switch (format) {
            case JSON -> canonical(json.readTree(content));
            case XML -> canonicalXml(parseXml(content).getDocumentElement());
            case YAML -> canonical(yaml.readTree(content));
            default -> content.replaceAll("\\s+", " ").strip();
        };
    }

    private DocumentNode tree(String content, Format format) throws IOException {
        if (format == Format.XML) return xmlTree(parseXml(content).getDocumentElement());
        JsonNode node = switch (format) {
            case JSON -> json.readTree(content); case YAML -> yaml.readTree(content);
            default -> null;
        };
        return node == null ? null : node("root", node);
    }

    private DocumentNode node(String name, JsonNode value) {
        if (value.isValueNode()) return new DocumentNode(name, value.asText(), List.of());
        List<DocumentNode> children = new ArrayList<>();
        if (value.isArray()) for (int index = 0; index < value.size(); index++) children.add(node("[" + index + "]", value.get(index)));
        else value.properties().forEach(entry -> children.add(node(entry.getKey(), entry.getValue())));
        return new DocumentNode(name, "", List.copyOf(children));
    }

    private static DocumentNode xmlTree(org.w3c.dom.Node node) {
        List<DocumentNode> children = new ArrayList<>();
        org.w3c.dom.NamedNodeMap attributes = node.getAttributes();
        if (attributes != null) for (int index = 0; index < attributes.getLength(); index++) {
            org.w3c.dom.Node attribute = attributes.item(index); children.add(new DocumentNode("@" + attribute.getNodeName(), attribute.getNodeValue(), List.of()));
        }
        StringBuilder text = new StringBuilder();
        for (org.w3c.dom.Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) children.add(xmlTree(child));
            else if (child.getNodeType() == org.w3c.dom.Node.TEXT_NODE && !child.getTextContent().isBlank()) text.append(child.getTextContent().strip());
        }
        return new DocumentNode(node.getNodeName(), text.toString(), List.copyOf(children));
    }

    private static String canonicalXml(org.w3c.dom.Node node) {
        StringBuilder value = new StringBuilder("<").append(node.getNamespaceURI() == null ? "" : "{" + node.getNamespaceURI() + "}").append(node.getLocalName() == null ? node.getNodeName() : node.getLocalName());
        org.w3c.dom.NamedNodeMap attributes = node.getAttributes(); List<String> canonicalAttributes = new ArrayList<>();
        if (attributes != null) for (int index = 0; index < attributes.getLength(); index++) { org.w3c.dom.Node attribute = attributes.item(index);
            if (!attribute.getNodeName().startsWith("xmlns")) canonicalAttributes.add((attribute.getNamespaceURI() == null ? "" : "{" + attribute.getNamespaceURI() + "}") + attribute.getNodeName() + "=" + attribute.getNodeValue()); }
        canonicalAttributes.stream().sorted().forEach(attribute -> value.append('|').append(attribute)); value.append('>');
        for (org.w3c.dom.Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) value.append(canonicalXml(child));
            else if (child.getNodeType() == org.w3c.dom.Node.TEXT_NODE && !child.getTextContent().isBlank()) value.append(child.getTextContent().strip().replaceAll("\\s+", " "));
        }
        return value.append("</>").toString();
    }

    private static String pretty(ObjectMapper mapper, String content) throws JsonProcessingException {
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readTree(content));
    }

    private static String canonical(JsonNode node) throws JsonProcessingException {
        return new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsString(sort(node));
    }

    private static String xml(String content, boolean indent) throws IOException {
        try {
            org.w3c.dom.Document document = parseXml(content); document.normalizeDocument();
            TransformerFactory factory = TransformerFactory.newInstance(); factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            javax.xml.transform.Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no"); transformer.setOutputProperty(OutputKeys.INDENT, indent ? "yes" : "no");
            if (indent) transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            StringWriter writer = new StringWriter(); transformer.transform(new DOMSource(document), new StreamResult(writer)); return writer.toString();
        } catch (javax.xml.transform.TransformerException exception) {
            throw new IOException("Invalid XML: " + exception.getMessage(), exception);
        }
    }

    private static org.w3c.dom.Document parseXml(String content) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance(); factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(content)));
        } catch (javax.xml.parsers.ParserConfigurationException | org.xml.sax.SAXException exception) {
            throw new IOException("Invalid XML: " + exception.getMessage(), exception);
        }
    }

    private static JsonNode sort(JsonNode node) {
        if (node.isObject()) {
            com.fasterxml.jackson.databind.node.ObjectNode sorted = new ObjectMapper().createObjectNode();
            List<Map.Entry<String, JsonNode>> fields = new ArrayList<>(node.properties());
            fields.stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> sorted.set(entry.getKey(), sort(entry.getValue()))); return sorted;
        }
        if (node.isArray()) { com.fasterxml.jackson.databind.node.ArrayNode array = new ObjectMapper().createArrayNode(); node.forEach(value -> array.add(sort(value))); return array; }
        return node;
    }

    private static String properties(String content) throws IOException {
        Properties values = new Properties(); values.load(new StringReader(content));
        return values.stringPropertyNames().stream().sorted().map(key -> key + "=" + values.getProperty(key)).collect(java.util.stream.Collectors.joining(System.lineSeparator()));
    }

    private static String csv(String content) {
        char separator = content.lines().findFirst().orElse("").chars().filter(value -> value == '\t').count() > 0 ? '\t' : ',';
        List<List<String>> rows = content.lines().map(line -> csvRow(line, separator)).toList();
        int columns = rows.stream().mapToInt(List::size).max().orElse(0); int[] widths = new int[columns];
        rows.forEach(row -> { for (int index = 0; index < row.size(); index++) widths[index] = Math.min(80, Math.max(widths[index], row.get(index).length())); });
        return rows.stream().map(row -> { List<String> padded = new ArrayList<>(); for (int index = 0; index < row.size(); index++) padded.add(String.format("%-" + widths[index] + "s", row.get(index))); return String.join(" | ", padded).stripTrailing(); }).collect(java.util.stream.Collectors.joining(System.lineSeparator()));
    }

    private static List<String> csvRow(String line, char separator) {
        List<String> values = new ArrayList<>(); StringBuilder value = new StringBuilder(); boolean quoted = false;
        for (int index = 0; index < line.length(); index++) { char current = line.charAt(index); if (current == '"') { if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') { value.append('"'); index++; } else quoted = !quoted; } else if (current == separator && !quoted) { values.add(value.toString()); value.setLength(0); } else value.append(current); }
        values.add(value.toString()); return values;
    }

    private static String sql(String content) { return SQL_KEYWORDS.matcher(content.strip()).replaceAll(match -> System.lineSeparator() + match.group(1).toUpperCase(Locale.ROOT) + " "); }
    private static String humanSize(long bytes) { return String.format(Locale.ROOT, "%.1f MiB", bytes / 1024.0 / 1024.0); }
}
