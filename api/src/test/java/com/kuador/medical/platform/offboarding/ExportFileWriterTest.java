package com.kuador.medical.platform.offboarding;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Files of a data export (MEDK-70): JSON document and ZIP of CSV files. */
class ExportFileWriterTest {

    private static final Instant AT = Instant.parse("2030-07-01T15:00:00Z");

    @Test
    void csvQuotesSeparatorsAndNeutralizesFormulas() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", "Clínica \"Sur\", Quito");
        row.put("note", "=HYPERLINK(\"x\")");
        row.put("amount", new BigDecimal("49.00"));
        row.put("empty", null);
        row.put("lines", "a\nb");

        String csv = ExportFileWriter.csv(List.of(row));

        assertThat(csv).isEqualTo("name,note,amount,empty,lines\r\n"
                + "\"Clínica \"\"Sur\"\", Quito\",\"'=HYPERLINK(\"\"x\"\")\",49.00,,\"a\nb\"\r\n");
        assertThat(ExportFileWriter.cell(-1)).as("numbers are never prefixed").isEqualTo("-1");
        assertThat(ExportFileWriter.cell("-1")).isEqualTo("'-1");
    }

    @Test
    void csvHeaderHasEveryColumnOfEveryRow() {
        assertThat(ExportFileWriter.csv(List.of(Map.of("a", 1), Map.of("b", 2))))
                .isEqualTo("a,b\r\n1,\r\n,2\r\n");
        assertThat(ExportFileWriter.csv(List.of())).isEqualTo("\r\n");
    }

    @Test
    void aCsvExportIsAZipWithOneFilePerSection() throws IOException {
        Map<String, List<Map<String, Object>>> sections = new LinkedHashMap<>();
        sections.put("platform.clinic", List.of(Map.of("city", "Quito")));
        sections.put("platform.subscription", List.of(Map.of("status", "CANCELLED")));

        byte[] zip = ExportFileWriter.csvZip(sections);

        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                entries.put(entry.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        assertThat(entries).containsOnlyKeys("platform.clinic.csv", "platform.subscription.csv");
        assertThat(entries.get("platform.clinic.csv")).isEqualTo("﻿city\r\nQuito\r\n");
    }

    @Test
    void aJsonExportNamesTheClinicAndKeepsAmountsExact() {
        UUID clinic = UUID.randomUUID();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("amount", new BigDecimal("49.10"));
        row.put("at", AT);
        row.put("status", ExportFormat.JSON);

        byte[] file = ExportFileWriter.json(clinic, AT, Map.of("platform.subscription", List.of(row)),
                JsonMapper.builder().build());

        @SuppressWarnings("unchecked")
        Map<String, Object> document = JsonMapper.builder().build().readValue(file, Map.class);
        assertThat(document).containsEntry("format", "kuador-medical-export").containsEntry("version", 1)
                .containsEntry("clinicId", clinic.toString()).containsEntry("generatedAt", "2030-07-01T15:00:00Z");
        assertThat(document.get("sections")).isEqualTo(Map.of("platform.subscription",
                List.of(Map.of("amount", "49.10", "at", "2030-07-01T15:00:00Z", "status", "JSON"))));
    }
}
