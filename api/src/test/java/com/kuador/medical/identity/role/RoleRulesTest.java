package com.kuador.medical.identity.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import com.kuador.medical.identity.Permission;
import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import com.kuador.medical.shared.web.FieldViolation;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

/** Domain rules of clinic roles (FR-006, FR-008, FR-012), without the database. */
class RoleRulesTest {

    @Test
    void customRoleKeepsTrimmedNameAndDescription() {
        Role role = Role.custom("  Reception  ", "  Front desk  ");

        assertThat(role.getName()).isEqualTo("Reception");
        assertThat(role.getDescription()).isEqualTo("Front desk");
        assertThat(role.isSystemRole()).isFalse();
    }

    @Test
    void blankDescriptionIsStoredAsNoDescription() {
        assertThat(Role.custom("Reception", "   ").getDescription()).isNull();
        assertThat(Role.custom("Reception", null).getDescription()).isNull();
    }

    @Test
    void ownerCannotBeUpdated() {
        Role role = Role.system(SystemRole.OWNER, "OWNER", null);

        assertThatThrownBy(() -> role.update("Renamed", null, role.getVersion()))
                .asInstanceOf(type(ApiException.class))
                .satisfies(e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.code()).isEqualTo(ErrorCode.SYSTEM_ROLE_IMMUTABLE);
                });
        assertThat(role.getName()).isEqualTo("OWNER");
    }

    @Test
    void adminCanBeUpdatedButKeepsItsSystemKey() {
        Role role = Role.system(SystemRole.ADMIN, "ADMIN", null);

        role.update("Administración", "Gestión de la clínica", role.getVersion());

        assertThat(role.getName()).isEqualTo("Administración");
        assertThat(role.getSystemCode()).isEqualTo(SystemRole.ADMIN);
        assertThat(role.isSystemRole()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(SystemRole.class)
    void systemRolesCannotBeDeleted(SystemRole systemRole) {
        assertThatThrownBy(() -> Role.system(systemRole, systemRole.name(), null).ensureDeletable())
                .asInstanceOf(type(ApiException.class))
                .extracting(ApiException::code)
                .isEqualTo(ErrorCode.SYSTEM_ROLE_IMMUTABLE);
    }

    @Test
    void customRoleCanBeDeleted() {
        Role.custom("Reception", null).ensureDeletable();
    }

    @Test
    void updateWithTheCurrentVersionReplacesNameAndDescription() {
        Role role = Role.custom("Reception", "Front desk");

        role.update(" Front desk ", "", 0);

        assertThat(role.getName()).isEqualTo("Front desk");
        assertThat(role.getDescription()).isNull();
    }

    @Test
    void updateWithAStaleVersionIsAConflict() {
        Role role = Role.custom("Reception", null);

        assertThatThrownBy(() -> role.update("Front desk", null, 3))
                .asInstanceOf(type(ApiException.class))
                .satisfies(e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.code()).isEqualTo(ErrorCode.VERSION_CONFLICT);
                });
        assertThat(role.getName()).isEqualTo("Reception");
    }

    @Test
    void assignablePermissionsAreReturnedInCatalogOrder() {
        List<Permission> permissions = AssignablePermissions.parse(List.of("roles.delete", "roles.read"));

        assertThat(permissions).containsExactly(Permission.ROLES_READ, Permission.ROLES_DELETE);
    }

    @Test
    void noPermissionsIsAllowed() {
        assertThat(AssignablePermissions.parse(List.of())).isEmpty();
    }

    @Test
    void unknownPermissionIsRejectedWithItsPosition() {
        assertThatThrownBy(() -> AssignablePermissions.parse(List.of("roles.read", "billing.unknown")))
                .asInstanceOf(type(ApiException.class))
                .satisfies(e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION);
                    assertThat(e.errors()).containsExactly(new FieldViolation("permissions[1]", "UNKNOWN_PERMISSION"));
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "platform.permissions.read",
        "platform.plans.manage",
        "platform.tenants.manage",
        "platform.tenants.purge",
        "platform.settings.manage"
    })
    void platformPermissionIsNeverAssignableToAClinicRole(String code) {
        assertThatThrownBy(() -> AssignablePermissions.parse(List.of(code)))
                .asInstanceOf(type(ApiException.class))
                .satisfies(e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION);
                    assertThat(e.errors()).containsExactly(new FieldViolation("permissions[0]", "UNKNOWN_PERMISSION"));
                });
    }

    @Test
    void repeatedPermissionIsRejected() {
        assertThatThrownBy(() -> AssignablePermissions.parse(List.of("roles.read", "roles.read")))
                .asInstanceOf(type(ApiException.class))
                .satisfies(e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION);
                    assertThat(e.errors()).containsExactly(new FieldViolation("permissions[1]", "DUPLICATE"));
                });
    }
}
