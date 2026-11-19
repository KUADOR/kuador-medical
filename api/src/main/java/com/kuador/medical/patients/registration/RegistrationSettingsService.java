package com.kuador.medical.patients.registration;

import com.kuador.medical.audit.AuditAction;
import com.kuador.medical.audit.AuditRecorder;
import com.kuador.medical.identity.AuthenticatedAccount;
import com.kuador.medical.identity.TenantDirectory;
import com.kuador.medical.patients.registration.Responses.RegistrationSettingsResponse;
import com.kuador.medical.shared.tenancy.TenantContext;
import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-registration settings of each clinic: disabled by default, with a public code for the
 * self-registration link, created the first time they are read. Native SQL with an explicit
 * clinic filter (ADR-007): {@link #enabledClinic} is the only lookup without a clinic, because a
 * self-registration arrives before the clinic is known; it reveals only clinics that enabled it.
 */
@Service
class RegistrationSettingsService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final int MAX_SLUG = 40;

    private final JdbcTemplate jdbc;
    private final TenantDirectory tenants;
    private final AuditRecorder audit;

    RegistrationSettingsService(JdbcTemplate jdbc, TenantDirectory tenants, AuditRecorder audit) {
        this.jdbc = jdbc;
        this.tenants = tenants;
        this.audit = audit;
    }

    @Transactional
    RegistrationSettingsResponse get() {
        return ensure(currentTenant());
    }

    @Transactional
    RegistrationSettingsResponse update(UpdateRegistrationSettingsRequest request, AuthenticatedAccount caller) {
        UUID tenantId = currentTenant();
        ensure(tenantId);
        int updated = jdbc.update("""
                update patients_registration_settings
                set self_registration_enabled = ?, version = version + 1, updated_at = ?, updated_by = ?
                where tenant_id = ? and version = ?""",
                request.selfRegistrationEnabled(), Timestamp.from(Instant.now()), caller.accountId(), tenantId,
                request.version());
        if (updated == 0) {
            throw ApiException.conflict(ErrorCode.VERSION_CONFLICT, "The settings were modified by someone else.");
        }
        audit.record(AuditAction.PATIENT_REGISTRATION_SETTINGS_UPDATED, tenantId, caller.accountId(), null);
        return find(tenantId).orElseThrow();
    }

    /** The active clinic with this public code, only if it enabled self-registration. */
    @Transactional(readOnly = true)
    Optional<UUID> enabledClinic(String publicCode) {
        return jdbc.queryForList("""
                        select tenant_id from patients_registration_settings
                        where public_code = ? and self_registration_enabled""", UUID.class, publicCode)
                .stream()
                .filter(tenantId -> tenants.find(tenantId).map(TenantDirectory.TenantInfo::active).orElse(false))
                .findFirst();
    }

    private RegistrationSettingsResponse ensure(UUID tenantId) {
        Optional<RegistrationSettingsResponse> found = find(tenantId);
        while (found.isEmpty()) {
            // A public code taken by another clinic inserts nothing: try another one.
            jdbc.update("""
                    insert into patients_registration_settings (tenant_id, public_code) values (?, ?)
                    on conflict do nothing""", tenantId, newPublicCode(tenantId));
            found = find(tenantId);
        }
        return found.get();
    }

    private Optional<RegistrationSettingsResponse> find(UUID tenantId) {
        List<RegistrationSettingsResponse> rows = jdbc.query("""
                        select self_registration_enabled, public_code, version from patients_registration_settings
                        where tenant_id = ?""",
                (row, index) -> new RegistrationSettingsResponse(
                        row.getBoolean("self_registration_enabled"), row.getString("public_code"), row.getLong("version")),
                tenantId);
        return rows.stream().findFirst();
    }

    /** {@code <slug of the clinic name>-<6 random characters>}, for example {@code clinica-norte-x7k2qa}. */
    private String newPublicCode(UUID tenantId) {
        String name = tenants.find(tenantId).map(TenantDirectory.TenantInfo::name).orElse("");
        String slug = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+)|(-+$)", "");
        if (slug.length() > MAX_SLUG) {
            slug = slug.substring(0, MAX_SLUG).replaceAll("-+$", "");
        }
        if (slug.isEmpty()) {
            slug = "clinica";
        }
        StringBuilder suffix = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            suffix.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return slug + "-" + suffix;
    }

    private static UUID currentTenant() {
        return TenantContext.current().orElseThrow(() -> new IllegalStateException("No clinic is bound"));
    }
}
