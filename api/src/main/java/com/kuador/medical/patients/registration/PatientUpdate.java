package com.kuador.medical.patients.registration;

import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.FieldViolation;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Body of {@code PATCH /patients/{id}}: only the given fields change. Read from a map because
 * {@code email: null} (remove it) differs from leaving it out (keep it). Mandatory fields cannot
 * be removed.
 */
record PatientUpdate(Map<String, Object> changes, long version) {

    private static final Set<String> FIELDS = Set.of(
            "documentType", "documentNumber", "firstNames", "lastNames", "birthDate", "sex",
            "email", "phone", "address", "version");
    private static final Pattern DOCUMENT_TYPE = Pattern.compile(Requests.DOCUMENT_TYPE);
    private static final Pattern SEX = Pattern.compile(Requests.SEX);
    private static final Pattern PHONE = Pattern.compile(Requests.PHONE);
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+$");

    static PatientUpdate parse(Map<String, Object> body) {
        List<FieldViolation> violations = new ArrayList<>();
        body.keySet().stream().filter(field -> !FIELDS.contains(field)).sorted()
                .forEach(field -> violations.add(new FieldViolation(field, "UNKNOWN_FIELD")));
        Map<String, Object> changes = new java.util.HashMap<>();
        text(body, "documentType", 30, false, DOCUMENT_TYPE, violations, changes);
        text(body, "documentNumber", 30, false, null, violations, changes);
        text(body, "firstNames", 120, false, null, violations, changes);
        text(body, "lastNames", 120, false, null, violations, changes);
        text(body, "sex", 30, false, SEX, violations, changes);
        text(body, "email", 254, true, EMAIL, violations, changes);
        text(body, "phone", 20, true, PHONE, violations, changes);
        text(body, "address", 250, true, null, violations, changes);
        if (body.containsKey("birthDate")) {
            Object value = body.get("birthDate");
            if (value == null) {
                violations.add(new FieldViolation("birthDate", "REQUIRED"));
            } else {
                try {
                    changes.put("birthDate", LocalDate.parse(value.toString()));
                } catch (DateTimeParseException e) {
                    violations.add(new FieldViolation("birthDate", "INVALID_FORMAT"));
                }
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
        return new PatientUpdate(changes, version);
    }

    /** The data of the patient with these changes applied. */
    PatientData applyTo(PatientData current) {
        return new PatientData(
                (String) changes.getOrDefault("documentType", current.documentType()),
                (String) changes.getOrDefault("documentNumber", current.documentNumber()),
                (String) changes.getOrDefault("firstNames", current.firstNames()),
                (String) changes.getOrDefault("lastNames", current.lastNames()),
                (LocalDate) changes.getOrDefault("birthDate", current.birthDate()),
                (String) changes.getOrDefault("sex", current.sex()),
                changes.containsKey("email") ? (String) changes.get("email") : current.email(),
                changes.containsKey("phone") ? (String) changes.get("phone") : current.phone(),
                changes.containsKey("address") ? (String) changes.get("address") : current.address());
    }

    private static void text(
            Map<String, Object> body,
            String field,
            int maxLength,
            boolean nullable,
            @Nullable Pattern format,
            List<FieldViolation> violations,
            Map<String, Object> changes) {
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
        if (!(value instanceof String text)) {
            violations.add(new FieldViolation(field, "INVALID_FORMAT"));
            return;
        }
        if (text.length() > maxLength) {
            violations.add(new FieldViolation(field, "TOO_LONG"));
            return;
        }
        if (format != null && !format.matcher(text).matches()) {
            violations.add(new FieldViolation(field, "INVALID_FORMAT"));
            return;
        }
        changes.put(field, text);
    }

    @Override
    public String toString() {
        return "PatientUpdate[redacted]";
    }
}
