package com.kuador.medical.identity.membership;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Finds the clinic of an account at login, the only moment no tenant is bound yet. Native SQL,
 * filtered explicitly by account (ADR-007); everything after login goes through @TenantId.
 */
@Component
public class MembershipLookup {

    private final JdbcTemplate jdbc;

    MembershipLookup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Clinic of the active membership; in the skeleton an account has at most one (ADR-010). */
    public Optional<UUID> activeTenantOf(UUID accountId) {
        List<UUID> tenants = jdbc.queryForList(
                "select tenant_id from tenant_membership where user_account_id = ? and status = 'ACTIVE' order by created_at",
                UUID.class, accountId);
        return tenants.stream().findFirst();
    }
}
