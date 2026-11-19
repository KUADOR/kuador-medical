package com.kuador.medical.platform;

/**
 * Body of {@code GET /system/info}: {@code status} is the overall status (DOWN when a required
 * dependency is down) and {@code database} the status of the PostgreSQL connection.
 */
public record SystemInfo(String name, String version, HealthStatus status, HealthStatus database) {}
