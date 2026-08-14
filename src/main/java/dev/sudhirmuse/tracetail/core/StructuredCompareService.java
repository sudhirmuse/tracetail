/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class StructuredCompareService {
    private static final int LCS_LIMIT = 2_000;
    public enum Kind { SAME, ADDED, REMOVED, CHANGED }
    public record Row(Integer leftLine, String left, Integer rightLine, String right, Kind kind) { }
    public record Result(boolean equivalent, String summary, List<Row> rows, String unified) { }

    public Result compare(Path left, Path right, Charset charset, boolean semantic, boolean ignoreWhitespace) throws IOException {
        StructuredDocumentService service = new StructuredDocumentService();
        StructuredDocumentService.Document a = service.load(left, charset); StructuredDocumentService.Document b = service.load(right, charset);
        boolean compatible = a.format() == b.format();
        if (semantic && compatible && (a.format() == StructuredDocumentService.Format.JSON || a.format() == StructuredDocumentService.Format.XML || a.format() == StructuredDocumentService.Format.YAML)) {
            boolean same = service.canonical(a.raw(), a.format()).equals(service.canonical(b.raw(), b.format()));
            if (same) return new Result(true, "Semantically equivalent " + a.format() + " documents", List.of(), "Files are semantically equivalent.\n");
        }
        String leftText = semantic && compatible ? a.formatted() : a.raw(); String rightText = semantic && compatible ? b.formatted() : b.raw();
        List<String> leftLines = leftText.lines().toList(), rightLines = rightText.lines().toList();
        List<Row> rows = leftLines.size() <= LCS_LIMIT && rightLines.size() <= LCS_LIMIT
            ? lcs(leftLines, rightLines, ignoreWhitespace) : positional(leftLines, rightLines, ignoreWhitespace);
        long changes = rows.stream().filter(row -> row.kind() != Kind.SAME).count();
        return new Result(changes == 0, changes == 0 ? "Files are equivalent" : changes + " changed row(s)", rows, unified(rows));
    }

    private static List<Row> lcs(List<String> left, List<String> right, boolean ignoreWhitespace) {
        int[][] lengths = new int[left.size() + 1][right.size() + 1];
        for (int i = left.size() - 1; i >= 0; i--) for (int j = right.size() - 1; j >= 0; j--)
            lengths[i][j] = equal(left.get(i), right.get(j), ignoreWhitespace) ? lengths[i + 1][j + 1] + 1 : Math.max(lengths[i + 1][j], lengths[i][j + 1]);
        List<Row> rows = new ArrayList<>(); int i = 0, j = 0;
        while (i < left.size() || j < right.size()) {
            if (i < left.size() && j < right.size() && equal(left.get(i), right.get(j), ignoreWhitespace)) rows.add(new Row(++i, left.get(i - 1), ++j, right.get(j - 1), Kind.SAME));
            else if (j < right.size() && (i == left.size() || lengths[i][j + 1] >= lengths[i + 1][j])) rows.add(new Row(null, "", ++j, right.get(j - 1), Kind.ADDED));
            else rows.add(new Row(++i, left.get(i - 1), null, "", Kind.REMOVED));
        }
        return rows;
    }

    private static List<Row> positional(List<String> left, List<String> right, boolean ignoreWhitespace) {
        List<Row> rows = new ArrayList<>(); int count = Math.max(left.size(), right.size());
        for (int index = 0; index < count; index++) { String a = index < left.size() ? left.get(index) : ""; String b = index < right.size() ? right.get(index) : "";
            Kind kind = index >= left.size() ? Kind.ADDED : index >= right.size() ? Kind.REMOVED : equal(a, b, ignoreWhitespace) ? Kind.SAME : Kind.CHANGED;
            rows.add(new Row(index < left.size() ? index + 1 : null, a, index < right.size() ? index + 1 : null, b, kind)); }
        return rows;
    }

    private static boolean equal(String left, String right, boolean ignoreWhitespace) { return ignoreWhitespace ? left.replaceAll("\\s+", "").equals(right.replaceAll("\\s+", "")) : left.equals(right); }
    private static String unified(List<Row> rows) { StringBuilder value = new StringBuilder(); for (Row row : rows) switch (row.kind()) {
        case SAME -> value.append("  ").append(row.left()).append('\n'); case ADDED -> value.append("+ ").append(row.right()).append('\n');
        case REMOVED -> value.append("- ").append(row.left()).append('\n'); case CHANGED -> value.append("- ").append(row.left()).append("\n+ ").append(row.right()).append('\n'); } return value.toString(); }
}
