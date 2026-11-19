package com.kuador.medical.identity.staff;

import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.FieldViolation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Body of {@code PATCH /staff-users/{id}}: only the given fields change. Read from a map because
 * {@code professionalRegistration: null} (remove it) differs from leaving it out (keep it).
 */
record StaffUserUpdate(
        @Nullable String firstNames,
        @Nullable String lastNames,
        @Nullable String jobTitle,
        boolean changesRegistration,
        @Nullable String professionalRegistration,
        long version) {

    private static final Set<String> FIELDS =
            Set.of("firstNames", "lastNames", "jobTitle", "professionalRegistration", "version");

    static StaffUserUpdate parse(Map<String, Object> body) {
        List<FieldViolation> violations = new ArrayList<>();
        body.keySet().stream().filter(field -> !FIELDS.contains(field)).sorted()
                .forEach(field -> violations.add(new FieldViolation(field, "UNKNOWN_FIELD")));
        String firstNames = text(body, "firstNames", 120, false, violations);
        String lastNames = text(body, "lastNames", 120, false, violations);
        String jobTitle = text(body, "jobTitle", 120, false, violations);
        boolean changesRegistration = body.containsKey("professionalRegistration");
        String registration = text(body, "professionalRegistration", 60, true, violations);
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
        if (firstNames == null && lastNames == null && jobTitle == null && !changesRegistration && violations.isEmpty()) {
            violations.add(new FieldViolation("", "NOTHING_TO_UPDATE"));
        }
        if (!violations.isEmpty()) {
            throw ApiException.validation(violations);
        }
        return new StaffUserUpdate(firstNames, lastNames, jobTitle, changesRegistration, registration, version);
    }

    private static @Nullable String text(
            Map<String, Object> body, String field, int maxLength, boolean nullable, List<FieldViolation> violations) {
        if (!body.containsKey(field)) {
            return null;
        }
        Object value = body.get(field);
        if (value == null) {
            if (!nullable) {
                violations.add(new FieldViolation(field, "REQUIRED"));
            }
            return null;
        }
        if (!(value instanceof String text)) {
            violations.add(new FieldViolation(field, "INVALID_FORMAT"));
            return null;
        }
        if (text.isBlank() && !nullable) {
            violations.add(new FieldViolation(field, "REQUIRED"));
            return null;
        }
        if (text.length() > maxLength) {
            violations.add(new FieldViolation(field, "TOO_LONG"));
            return null;
        }
        return text;
    }
}
