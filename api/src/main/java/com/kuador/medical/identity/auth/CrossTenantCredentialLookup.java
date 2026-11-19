package com.kuador.medical.identity.auth;

import com.kuador.medical.identity.account.EmailAddresses;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The only read across clinics of the system (ADR-012, exception 1 to Constitution I): the
 * active credentials of an email in every clinic, so that the login can verify the password in
 * each one and reveal only the clinics where it matched. It reads accounts and memberships only,
 * never business data, and only {@link AuthenticationService} may use it (enforced by an
 * architecture test). Native SQL filtered explicitly by email (ADR-007).
 */
@Component
class CrossTenantCredentialLookup {

    private final JdbcTemplate jdbc;

    CrossTenantCredentialLookup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * One entry per active membership of an active account with that email, plus the platform
     * account (without clinic) when the email is the SUPER_ADMIN one.
     */
    List<StoredCredential> activeCredentials(String email) {
        return jdbc.query("""
                select a.id, m.tenant_id, a.password_hash, a.platform_admin
                from user_account a
                left join tenant_membership m on m.user_account_id = a.id and m.status = 'ACTIVE'
                where a.email = ? and a.status = 'ACTIVE' and (a.platform_admin or m.tenant_id is not null)
                order by m.created_at""",
                (row, index) -> new StoredCredential(
                        row.getObject(1, UUID.class),
                        row.getBoolean(4) ? null : row.getObject(2, UUID.class),
                        row.getString(3),
                        row.getBoolean(4)),
                EmailAddresses.normalize(email));
    }
}
