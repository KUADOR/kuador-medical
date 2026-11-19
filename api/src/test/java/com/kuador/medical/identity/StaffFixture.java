package com.kuador.medical.identity;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Test data the API cannot create yet: branches (clinics module, later task) and staff users
 * (MEDK-16). Every value is random test data, never a real person or credential.
 */
public final class StaffFixture {

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public StaffFixture(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    public UUID branch(UUID tenantId, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into clinics_branch (id, tenant_id, name) values (?, ?, ?)", id, tenantId, name);
        return id;
    }

    /** A staff user of the clinic without assignments; returns its account id. */
    public UUID staffUser(UUID tenantId, String email, String password) {
        UUID accountId = UUID.randomUUID();
        jdbc.update("""
                insert into user_account (id, email, password_hash, display_name)
                values (?, ?, ?, 'Staff member')""", accountId, email, passwordEncoder.encode(password));
        jdbc.update("insert into tenant_membership (id, tenant_id, user_account_id) values (?, ?, ?)",
                UUID.randomUUID(), tenantId, accountId);
        return accountId;
    }

    public UUID staffUser(UUID tenantId) {
        return staffUser(tenantId, "staff-" + UUID.randomUUID() + "@example.test", "pw-" + UUID.randomUUID());
    }

    public UUID ownerOf(UUID tenantId) {
        return jdbc.queryForObject("""
                select a.user_id from identity_role_assignment a join identity_role r on r.id = a.role_id
                where a.tenant_id = ? and r.system_code = 'OWNER' order by a.created_at limit 1""",
                UUID.class, tenantId);
    }

    public UUID role(UUID tenantId, String suggestedKey) {
        return jdbc.queryForObject("select id from identity_role where tenant_id = ? and suggested_key = ?",
                UUID.class, tenantId, suggestedKey);
    }

    /** Assignments of a user as (branch id or null) → role id. */
    public List<Map<String, Object>> assignments(UUID userId) {
        return jdbc.queryForList(
                "select branch_id, role_id, tenant_id from identity_role_assignment where user_id = ? order by branch_id",
                userId);
    }

    /** Writes an assignment directly, for states the API forbids building (for example a second owner). */
    public void assign(UUID tenantId, UUID userId, UUID branchId, UUID roleId) {
        jdbc.update("""
                insert into identity_role_assignment (id, tenant_id, user_id, branch_id, role_id)
                values (?, ?, ?, ?, ?)""", UUID.randomUUID(), tenantId, userId, branchId, roleId);
    }
}
