package com.kuador.medical.platform.tenant;

import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Plan D8: a RUC has exactly 13 ASCII digits and is unique among non-purged clinics. The database
 * enforces both too ({@code tenant_ruc_format_check}, {@code tenant_ruc_unique_idx}). A RUC is
 * never logged nor put in an error detail.
 */
@Component
class RucValidator {

    /** Format of a RUC, shared with Bean Validation. */
    static final String FORMAT = "^[0-9]{13}$";

    private static final Pattern PATTERN = Pattern.compile(FORMAT);

    private final TenantRepository tenants;

    RucValidator(TenantRepository tenants) {
        this.tenants = tenants;
    }

    static boolean hasValidFormat(@Nullable String ruc) {
        return ruc != null && PATTERN.matcher(ruc).matches();
    }

    /** Throws 409 {@code RUC_ALREADY_REGISTERED} if another non-purged clinic has {@code ruc}. */
    void ensureAvailable(String ruc, @Nullable UUID except) {
        if (tenants.existsNotPurgedByRuc(ruc, except)) {
            throw alreadyRegistered();
        }
    }

    static ApiException alreadyRegistered() {
        return ApiException.conflict(ErrorCode.RUC_ALREADY_REGISTERED, "The RUC is already registered.");
    }
}
