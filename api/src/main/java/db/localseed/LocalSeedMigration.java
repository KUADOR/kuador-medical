package db.localseed;

import com.kuador.medical.identity.Permission;
import com.kuador.medical.identity.PermissionScope;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.migration.Context;
import org.flywaydb.core.api.migration.JavaMigration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

/**
 * Local development data (MEDK-26 plan, decision 3). Flyway only finds this class when its location
 * ({@value #LOCATION_PATH}) is configured, which only the "local" profile does. It creates two
 * fictitious clinics, A and B, each with one branch, the OWNER and ADMIN system roles and one
 * active staff user holding OWNER. The password of both users is {@code LOCAL_SEED_PASSWORD}
 * (Flyway placeholder {@value #PASSWORD_PLACEHOLDER}), stored only as an Argon2id hash like any
 * other password (ADR-006); it is never written in the repository nor logged.
 *
 * <p>Repeatable migration with a fixed checksum: it runs once, after every versioned migration,
 * and every insert is idempotent. To apply a new {@code LOCAL_SEED_PASSWORD}, recreate the local
 * database. No real personal data: names and emails are fictitious ({@code .example.test}).
 */
public class LocalSeedMigration implements JavaMigration {

    public static final String LOCATION_PATH = "db/localseed";
    public static final String DESCRIPTION = "local seed";
    public static final String PASSWORD_PLACEHOLDER = "localSeedPassword";
    static final String PASSWORD_VARIABLE = "LOCAL_SEED_PASSWORD";
    static final int MIN_PASSWORD_LENGTH = 12;

    public static final UUID TENANT_A = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    public static final UUID BRANCH_A = UUID.fromString("a0000000-0000-4000-8000-000000000002");
    public static final UUID USER_A = UUID.fromString("a0000000-0000-4000-8000-000000000005");
    public static final String CLINIC_A_NAME = "Clínica Demo A";
    public static final String USER_A_EMAIL = "usuario.a@clinica-a.example.test";

    public static final UUID TENANT_B = UUID.fromString("b0000000-0000-4000-8000-000000000001");
    public static final UUID BRANCH_B = UUID.fromString("b0000000-0000-4000-8000-000000000002");
    public static final UUID USER_B = UUID.fromString("b0000000-0000-4000-8000-000000000005");
    public static final String CLINIC_B_NAME = "Clínica Demo B";
    public static final String USER_B_EMAIL = "usuario.b@clinica-b.example.test";

    private static final int CHECKSUM = 1;

    @Override
    public MigrationVersion getVersion() {
        return null; // repeatable
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public Integer getChecksum() {
        return CHECKSUM;
    }

    @Override
    public boolean canExecuteInTransaction() {
        return true;
    }

    @Override
    public void migrate(Context context) throws SQLException {
        String password = password(context.getConfiguration().getPlaceholders());
        // Same format as identity's DelegatingPasswordEncoder: the algorithm id prefixes the hash.
        String hash = "{argon2}" + Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode(password);
        Connection connection = context.getConnection();
        seedClinic(connection, new SeedClinic(TENANT_A, CLINIC_A_NAME, BRANCH_A, USER_A, USER_A_EMAIL, "A"), hash);
        seedClinic(connection, new SeedClinic(TENANT_B, CLINIC_B_NAME, BRANCH_B, USER_B, USER_B_EMAIL, "B"), hash);
    }

    /** The seed password, read from the Flyway placeholders. The message names the variable, never the value. */
    static String password(Map<String, String> placeholders) {
        String password = placeholders.entrySet().stream()
                .filter(entry -> normalize(entry.getKey()).equals(normalize(PASSWORD_PLACEHOLDER)))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(PASSWORD_VARIABLE + " is missing: the local seed needs it");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(PASSWORD_VARIABLE + " must have at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        return password;
    }

    private static String normalize(String key) {
        return key.replace("-", "").replace("_", "").toLowerCase(Locale.ROOT);
    }

    private static void seedClinic(Connection connection, SeedClinic clinic, String passwordHash) throws SQLException {
        UUID ownerRole = derived(clinic.tenantId(), 3);
        UUID adminRole = derived(clinic.tenantId(), 4);
        update(connection, "insert into tenant (id, name, status) values (?, ?, 'ACTIVE') on conflict (id) do nothing",
                clinic.tenantId(), clinic.name());
        update(connection, "insert into clinics_branch (id, tenant_id, name) values (?, ?, ?) on conflict (id) do nothing",
                clinic.branchId(), clinic.tenantId(), "Sede principal");
        insertRole(connection, ownerRole, clinic.tenantId(), "OWNER", "Dueño de la clínica");
        insertRole(connection, adminRole, clinic.tenantId(), "ADMIN", "Administrador de la clínica");
        update(connection, """
                insert into identity_user_account (
                    id, tenant_id, email_normalized, password_hash, status, kind, first_names, last_names)
                values (?, ?, ?, ?, 'ACTIVE', 'STAFF', ?, ?)
                on conflict do nothing""",
                clinic.userId(), clinic.tenantId(), clinic.email(), passwordHash, "Usuario", "de prueba " + clinic.label());
        update(connection, """
                insert into identity_role_assignment (id, tenant_id, user_id, branch_id, role_id)
                values (?, ?, ?, null, ?)
                on conflict do nothing""",
                derived(clinic.tenantId(), 6), clinic.tenantId(), clinic.userId(), ownerRole);
    }

    private static void insertRole(Connection connection, UUID roleId, UUID tenantId, String systemCode, String description)
            throws SQLException {
        update(connection, """
                insert into identity_role (id, tenant_id, name, description, system_code, suggested_key)
                values (?, ?, ?, ?, ?, ?)
                on conflict do nothing""",
                roleId, tenantId, systemCode, description, systemCode, systemCode);
        for (Permission permission : Permission.ofScope(PermissionScope.TENANT)) {
            update(connection, """
                    insert into identity_role_permission (role_id, tenant_id, permission_code)
                    values (?, ?, ?)
                    on conflict do nothing""",
                    roleId, tenantId, permission.code());
        }
    }

    private static void update(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            statement.executeUpdate();
        }
    }

    /** Stable id of a seed row: the tenant id with its last digit replaced by {@code n}. */
    private static UUID derived(UUID tenantId, int n) {
        String id = tenantId.toString();
        return UUID.fromString(id.substring(0, id.length() - 1) + n);
    }

    private record SeedClinic(UUID tenantId, String name, UUID branchId, UUID userId, String email, String label) {}
}
