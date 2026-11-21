package com.kuador.medical.platform.tenant;

import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.FieldViolation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Body of {@code PATCH /platform/tenants/{tenantId}} (JSON merge patch): only the sent fields
 * change and {@code null} clears an optional field. Read from a map because {@code null} differs
 * from leaving a field out. Mandatory fields cannot be cleared; the plan and the subscription
 * change through their own operations.
 */
record TenantUpdate(Map<String, @Nullable Object> changes, long version) {

    private static final Set<String> FIELDS = Set.of(
            "legalName", "tradeName", "ruc", "clinicType", "countryCode", "city", "address", "phone",
            "contactEmail", "mspEstablishmentNumber", "version");
    private static final Pattern RUC = Pattern.compile(RucValidator.FORMAT);
    private static final Pattern COUNTRY = Pattern.compile(TenantRequests.COUNTRY_CODE);
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+$");

    static TenantUpdate parse(Map<String, @Nullable Object> body) {
        List<FieldViolation> violations = new ArrayList<>();
        body.keySet().stream().filter(field -> !FIELDS.contains(field)).sorted()
                .forEach(field -> violations.add(new FieldViolation(field, "UNKNOWN_FIELD")));
        Map<String, @Nullable Object> changes = new HashMap<>();
        text(body, "legalName", 1, 200, false, null, violations, changes);
        text(body, "tradeName", 1, 200, true, null, violations, changes);
        text(body, "ruc", 13, 13, false, RUC, violations, changes);
        text(body, "countryCode", 2, 2, false, COUNTRY, violations, changes);
        text(body, "city", 1, 120, false, null, violations, changes);
        text(body, "address", 1, 300, false, null, violations, changes);
        text(body, "phone", 7, 20, false, null, violations, changes);
        text(body, "contactEmail", 3, 254, false, EMAIL, violations, changes);
        text(body, "mspEstablishmentNumber", 1, 40, true, null, violations, changes);
        if (body.containsKey("clinicType")) {
            Object value = body.get("clinicType");
            if (value == null) {
                violations.add(new FieldViolation("clinicType", "REQUIRED"));
            } else if (Arrays.stream(ClinicType.values()).noneMatch(type -> type.name().equals(value))) {
                violations.add(new FieldViolation("clinicType", "INVALID_FORMAT"));
            } else {
                changes.put("clinicType", ClinicType.valueOf(value.toString()));
            }
        }
        long version = -1;
        Object rawVersion = body.get("version");
        if (rawVersion == null) {
            violations.add(new FieldViolation("version", "REQUIRED"));
        } else if (!(rawVersion instanceof Number number) || number.longValue() < 0
                || number.doubleValue() != number.longValue()) {
            violations.add(new FieldViolation("version", "INVALID_FORMAT"));
        } else {
            version = number.longValue();
        }
        if (changes.isEmpty() && violations.isEmpty()) {
            violations.add(new FieldViolation("", "NOTHING_TO_UPDATE"));
        }
        if (!violations.isEmpty()) {
            throw ApiException.validation(violations);
        }
        return new TenantUpdate(changes, version);
    }

    /** The new RUC, if the update changes it. */
    @Nullable String newRuc() {
        return (String) changes.get("ruc");
    }

    /** The clinic data with these changes applied. */
    ClinicData applyTo(ClinicData current) {
        return new ClinicData(
                (String) changes.getOrDefault("legalName", current.legalName()),
                changes.containsKey("tradeName") ? (String) changes.get("tradeName") : current.tradeName(),
                (String) changes.getOrDefault("ruc", current.ruc()),
                (ClinicType) changes.getOrDefault("clinicType", current.clinicType()),
                (String) changes.getOrDefault("countryCode", current.countryCode()),
                (String) changes.getOrDefault("city", current.city()),
                (String) changes.getOrDefault("address", current.address()),
                (String) changes.getOrDefault("phone", current.phone()),
                (String) changes.getOrDefault("contactEmail", current.contactEmail()),
                changes.containsKey("mspEstablishmentNumber")
                        ? (String) changes.get("mspEstablishmentNumber")
                        : current.mspEstablishmentNumber());
    }

    private static void text(
            Map<String, @Nullable Object> body,
            String field,
            int minLength,
            int maxLength,
            boolean nullable,
            @Nullable Pattern format,
            List<FieldViolation> violations,
            Map<String, @Nullable Object> changes) {
        if (!body.containsKey(field)) {
            return;
        }
        Object value = body.get(field);
        if (value == null || (value instanceof String blank && blank.isBlank())) {
            if (nullable) {
                changes.put(field, null);
            } else {
                violations.add(new FieldViolation(field, "REQUIRED"));
            }
            return;
        }
        if (!(value instanceof String raw)) {
            violations.add(new FieldViolation(field, "INVALID_FORMAT"));
            return;
        }
        String text = raw.strip();
        if (format != null && !format.matcher(text).matches()) {
            violations.add(new FieldViolation(field, "INVALID_FORMAT"));
            return;
        }
        if (text.length() < minLength || text.length() > maxLength) {
            violations.add(new FieldViolation(field, "INVALID_LENGTH"));
            return;
        }
        changes.put(field, text);
    }

    @Override
    public String toString() {
        return "TenantUpdate[redacted]";
    }
}
