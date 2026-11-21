package com.kuador.medical.platform.api;

import java.util.List;
import java.util.Map;

/**
 * SPI: the module that owns data of a clinic adds it to the data export of the clinic (ADR-018,
 * MEDK-49 FR-021). One bean per section. Each call runs with the clinic bound in the tenant context
 * (ADR-007) and in a read-only transaction, so it only sees that clinic.
 */
public interface TenantDataExporter {

    /**
     * Stable name of the section, in lower case with dots, for example {@code platform.subscription}.
     * It is the key of the section in the JSON file and the name of its CSV file.
     */
    String section();

    /**
     * Rows of the section. Values are {@code String}, {@code Number}, {@code Boolean},
     * {@code java.time} values, {@code UUID}, enums or {@code null}; every row of a section should
     * have the same keys, in the same order.
     */
    List<Map<String, Object>> rows();
}
