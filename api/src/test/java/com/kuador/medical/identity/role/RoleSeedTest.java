package com.kuador.medical.identity.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kuador.medical.identity.Permission;
import com.kuador.medical.identity.PermissionScope;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** The versioned role seed (identity/role-seed.yaml) and its validation. */
class RoleSeedTest {

    private final RoleSeed seed = RoleSeed.load();
    private final Map<String, RoleSeed.SeededRole> byKey =
            seed.roles().stream().collect(Collectors.toMap(RoleSeed.SeededRole::key, role -> role));

    @Test
    void ownerAndAdminHoldEveryClinicPermission() {
        assertThat(byKey.get("OWNER").system()).isEqualTo(SystemRole.OWNER);
        assertThat(byKey.get("ADMIN").system()).isEqualTo(SystemRole.ADMIN);
        assertThat(byKey.get("OWNER").permissions()).isEqualTo(Permission.ofScope(PermissionScope.TENANT));
        assertThat(byKey.get("ADMIN").permissions()).isEqualTo(Permission.ofScope(PermissionScope.TENANT));
    }

    @Test
    void receptionRegistersPatients() {
        assertThat(byKey.get("RECEPTION").permissions()).containsExactly(
                Permission.PATIENTS_READ, Permission.PATIENTS_REGISTER,
                Permission.PATIENTS_EDIT, Permission.PATIENTS_GUARDIANS_MANAGE);
    }

    @Test
    void seedsTheSuggestedRolesOfTheGlossary() {
        assertThat(byKey).containsOnlyKeys(
                "OWNER", "ADMIN", "RECEPTION", "DOCTOR", "DENTIST", "NURSE", "CASHIER",
                "PHARMACIST", "WAREHOUSE_KEEPER", "COLLECTIONS", "PATIENT");
        assertThat(seed.roles()).filteredOn(RoleSeed.SeededRole::clinical)
                .extracting(RoleSeed.SeededRole::key)
                .containsExactlyInAnyOrder("DOCTOR", "DENTIST", "NURSE");
        assertThat(seed.version()).isEqualTo(1);
    }

    @Test
    void platformPermissionInTheSeedIsRejected() {
        assertThatThrownBy(() -> parse("""
                version: 1
                roles:
                  - {key: OWNER, system: OWNER, name: OWNER, permissions: ["*"]}
                  - {key: ADMIN, system: ADMIN, name: ADMIN, permissions: [platform.permissions.read]}
                """)).hasMessageContaining("outside the clinic catalog");
    }

    @Test
    void seedWithoutOwnerIsRejected() {
        assertThatThrownBy(() -> parse("""
                version: 1
                roles:
                  - {key: ADMIN, system: ADMIN, name: ADMIN, permissions: ["*"]}
                """)).hasMessageContaining("OWNER");
    }

    @Test
    void repeatedKeyIsRejected() {
        assertThatThrownBy(() -> parse("""
                version: 1
                roles:
                  - {key: OWNER, system: OWNER, name: OWNER, permissions: ["*"]}
                  - {key: ADMIN, system: ADMIN, name: ADMIN, permissions: ["*"]}
                  - {key: ADMIN, name: Other, permissions: []}
                """)).hasMessageContaining("Repeated role key");
    }

    private static RoleSeed parse(String yaml) {
        return RoleSeed.parse(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }
}
