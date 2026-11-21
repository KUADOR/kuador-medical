package com.kuador.medical.platform.offboarding;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes the sections of an export: {@code JSON} as one document, {@code CSV} as a ZIP with one
 * UTF-8 CSV file per section (RFC 4180, with a byte order mark so spreadsheets read the accents).
 * Text cells starting with {@code = + - @} are prefixed with {@code '} so a spreadsheet never runs
 * them as formulas.
 */
final class ExportFileWriter {

    static final String FORMAT_NAME = "kuador-medical-export";
    static final int FORMAT_VERSION = 1;

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private ExportFileWriter() {}

    static byte[] write(ExportFormat format, UUID tenantId, Instant generatedAt,
            Map<String, List<Map<String, Object>>> sections, JsonMapper json) {
        return switch (format) {
            case JSON -> json(tenantId, generatedAt, sections, json);
            case CSV -> csvZip(sections);
        };
    }

    static byte[] json(UUID tenantId, Instant generatedAt, Map<String, List<Map<String, Object>>> sections,
            JsonMapper json) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("format", FORMAT_NAME);
        document.put("version", FORMAT_VERSION);
        document.put("clinicId", tenantId.toString());
        document.put("generatedAt", generatedAt.toString());
        Map<String, Object> rendered = new LinkedHashMap<>();
        sections.forEach((name, rows) -> rendered.put(name, rows.stream().map(ExportFileWriter::plain).toList()));
        document.put("sections", rendered);
        return json.writeValueAsBytes(document);
    }

    static byte[] csvZip(Map<String, List<Map<String, Object>>> sections) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, List<Map<String, Object>>> section : sections.entrySet()) {
                zip.putNextEntry(new ZipEntry(section.getKey() + ".csv"));
                zip.write(UTF8_BOM);
                zip.write(csv(section.getValue()).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    /** One CSV document: a header with every key, in order of appearance, then one line per row. */
    static String csv(List<Map<String, Object>> rows) {
        Set<String> columns = new LinkedHashSet<>();
        rows.forEach(row -> columns.addAll(row.keySet()));
        StringBuilder out = new StringBuilder();
        line(out, columns.stream().map(column -> (Object) column).toList());
        for (Map<String, Object> row : rows) {
            line(out, columns.stream().map(row::get).toList());
        }
        return out.toString();
    }

    private static void line(StringBuilder out, List<@Nullable Object> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(cell(values.get(i)));
        }
        out.append("\r\n");
    }

    static String cell(@Nullable Object value) {
        if (value == null) {
            return "";
        }
        String text = text(value);
        if (!(value instanceof Number) && !text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }

    private static Map<String, @Nullable Object> plain(Map<String, Object> row) {
        Map<String, @Nullable Object> out = new LinkedHashMap<>();
        row.forEach((key, value) -> out.put(key, value == null || value instanceof Number || value instanceof Boolean
                ? (value instanceof BigDecimal decimal ? decimal.toPlainString() : value)
                : text(value)));
        return out;
    }

    private static String text(Object value) {
        return value instanceof BigDecimal decimal ? decimal.toPlainString() : value.toString();
    }
}
