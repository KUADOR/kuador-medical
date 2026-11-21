package com.kuador.medical.platform.offboarding;

/** {@code ExportFormat} in {@code platform.openapi.yaml}: one JSON file, or a ZIP with one CSV file per section. */
enum ExportFormat {
    JSON("application/json", "json"),
    CSV("application/zip", "zip");

    private final String contentType;
    private final String extension;

    ExportFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    String contentType() {
        return contentType;
    }

    String extension() {
        return extension;
    }
}
