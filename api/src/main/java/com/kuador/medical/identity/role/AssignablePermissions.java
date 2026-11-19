package com.kuador.medical.identity.role;

import com.kuador.medical.identity.Permission;
import com.kuador.medical.identity.PermissionScope;
import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.FieldViolation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Permissions a clinic role may hold (FR-012): only clinic-scoped codes of the fixed catalog.
 * Unknown and platform codes are both {@code UNKNOWN_PERMISSION}, so the platform catalog is
 * never revealed to a clinic.
 */
public final class AssignablePermissions {

    static final String UNKNOWN_PERMISSION = "UNKNOWN_PERMISSION";
    static final String DUPLICATE = "DUPLICATE";

    private AssignablePermissions() {}

    /** Validates the requested codes and returns them in catalog order, or rejects with 400 VALIDATION. */
    public static List<Permission> parse(List<String> codes) {
        Set<Permission> accepted = EnumSet.noneOf(Permission.class);
        List<FieldViolation> violations = new ArrayList<>();
        for (int index = 0; index < codes.size(); index++) {
            String field = "permissions[" + index + "]";
            Optional<Permission> permission = Optional.ofNullable(codes.get(index))
                    .flatMap(Permission::fromCode)
                    .filter(found -> found.scope() == PermissionScope.TENANT);
            if (permission.isEmpty()) {
                violations.add(new FieldViolation(field, UNKNOWN_PERMISSION));
            } else if (!accepted.add(permission.get())) {
                violations.add(new FieldViolation(field, DUPLICATE));
            }
        }
        if (!violations.isEmpty()) {
            throw ApiException.validation(violations);
        }
        return accepted.stream().sorted(Comparator.naturalOrder()).toList();
    }
}
