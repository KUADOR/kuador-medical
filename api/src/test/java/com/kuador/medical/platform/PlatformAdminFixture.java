package com.kuador.medical.platform;

import com.kuador.medical.IdentityApi;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * A SUPER_ADMIN account that already changed its password (the bootstrap one belongs to
 * BootstrapAdminIT). Credentials are random test values, never real ones.
 */
public final class PlatformAdminFixture {

    private PlatformAdminFixture() {}

    /** Creates a platform account, logs it in and returns its access token and account id. */
    public static Admin create(IdentityApi api, JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        String email = IdentityApi.uniqueEmail("platform");
        String password = IdentityApi.randomPassword();
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into user_account (id, email, password_hash, display_name, platform_admin, must_change_password)
                values (?, ?, ?, 'Kuador', true, false)""",
                id, email, passwordEncoder.encode(password));
        return new Admin(id, api.token(email, password));
    }

    public record Admin(UUID accountId, String token) {}
}
