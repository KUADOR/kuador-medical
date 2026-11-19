package com.kuador.medical.identity.auth;

import com.kuador.medical.identity.account.EmailAddresses;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The only read across clinics of the system (ADR-012, exception 1 to Constitution I): the
 * active credentials of an email in every clinic (one account per clinic, ADR-012), so that the
 * login can verify the password in each one and reveal only the clinics where it matched. It
 * reads the account tables only, never business data, and only {@link AuthenticationService} may use it (enforced by an
 * architecture test). Native SQL filtered explicitly by email (ADR-007).
 */
@Component
class CrossTenantCredentialLookup {

    private final JdbcTemplate jdbc;

    CrossTenantCredentialLookup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * The platform account (without clinic) when the email is the SUPER_ADMIN one, then one entry
     * per active clinic account with that email, oldest first. Invited accounts have no password
     * yet and are left out.
     */
    List<StoredCredential> activeCredentials(String email) {
        String normalized = EmailAddresses.normalize(email);
        return jdbc.query("""
                select id, null::uuid as tenant_id, password_hash, true as platform_admin, created_at
                from user_account
                where email = ? and status = 'ACTIVE' and platform_admin
                union all
                select id, tenant_id, password_hash, false, created_at
                from identity_user_account
                where email_normalized = ? and status = 'ACTIVE' and password_hash is not null
                order by platform_admin desc, created_at, id""",
                (row, index) -> new StoredCredential(
                        row.getObject(1, UUID.class),
                        row.getObject(2, UUID.class),
                        row.getString(3),
                        row.getBoolean(4)),
                normalized, normalized);
    }
}
