package com.kuador.medical.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Fixed permission catalog (ADR-003, MEDK-12 plan § Catálogo de permisos). */
class PermissionCatalogTest {

    /** Same pattern as {@code PermissionCode} in {@code contracts/user-registration.openapi.yaml}. */
    private static final String CODE_PATTERN = "^[a-z]+(\\.[a-z][a-zA-Z]*)+$";

    @ParameterizedTest
    @ValueSource(strings = {
        "identity.users.read",
        "identity.users.manage",
        "identity.assignments.manage",
        "patients.read",
        "patients.register",
        "patients.edit",
        "patients.guardians.manage",
        "patients.selfRegistration.configure"
    })
    void userRegistrationPermissionsAreClinicScoped(String code) {
        assertThat(Permission.fromCode(code))
                .as("catalog entry %s", code)
                .hasValueSatisfying(permission -> assertThat(permission.scope()).isEqualTo(PermissionScope.TENANT));
    }

    @Test
    void roleManagementKeepsItsPublishedCodes() {
        assertThat(Permission.ofScope(PermissionScope.TENANT))
                .extracting(Permission::code)
                .contains("roles.read", "roles.create", "roles.update", "roles.delete");
    }

    @Test
    void platformPermissionsAreNeverClinicScoped() {
        assertThat(Permission.ofScope(PermissionScope.PLATFORM))
                .extracting(Permission::code)
                .containsExactly(
                        "platform.permissions.read",
                        "platform.plans.manage",
                        "platform.tenants.manage",
                        "platform.tenants.purge",
                        "platform.settings.manage");
    }

    @Test
    void everyPlatformCodeIsPlatformScoped() {
        assertThat(Permission.values())
                .filteredOn(permission -> permission.code().startsWith("platform."))
                .allSatisfy(permission -> assertThat(permission.scope()).isEqualTo(PermissionScope.PLATFORM));
    }

    @Test
    void codesAreUnique() {
        List<String> codes = Arrays.stream(Permission.values()).map(Permission::code).toList();

        assertThat(codes).doesNotHaveDuplicates();
    }

    @ParameterizedTest
    @EnumSource(Permission.class)
    void everyCodeMatchesTheContractPatternAndHasADescription(Permission permission) {
        assertThat(permission.code()).matches(CODE_PATTERN);
        assertThat(permission.description()).isNotBlank();
    }
}
