package com.kuador.medical.identity.auth;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Active clinic accounts of an email in every clinic, read at login before any clinic is bound
 * (ADR-012, exception 1 to principle I). Only {@link LoginService} uses it; it reads only the
 * account table and returns credentials for the caller to verify, never data of other clinics.
 * MEDK-15 replaces it with {@code CrossTenantCredentialLookup} and the clinic selection.
 */
@Component
class ClinicCredentialLookup {

    private final JdbcTemplate jdbc;

    ClinicCredentialLookup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    record Candidate(UUID accountId, UUID tenantId, String passwordHash) {

        @Override
        public String toString() {
            return "Candidate[" + accountId + ", redacted]";
        }
    }

    /** {@code email} must already be normalized. Oldest account first. */
    List<Candidate> activeAccounts(String email) {
        return jdbc.query("""
                        select id, tenant_id, password_hash from identity_user_account
                        where email_normalized = ? and status = 'ACTIVE' and password_hash is not null
                        order by created_at, id""",
                (row, index) -> new Candidate(
                        row.getObject("id", UUID.class), row.getObject("tenant_id", UUID.class), row.getString("password_hash")),
                email);
    }
}
