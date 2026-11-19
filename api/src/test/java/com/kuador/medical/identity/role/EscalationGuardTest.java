package com.kuador.medical.identity.role;

import static com.kuador.medical.identity.Permission.ASSIGNMENTS_MANAGE;
import static com.kuador.medical.identity.Permission.PATIENTS_EDIT;
import static com.kuador.medical.identity.Permission.PATIENTS_READ;
import static com.kuador.medical.identity.Permission.PATIENTS_REGISTER;
import static com.kuador.medical.identity.Permission.ROLES_READ;
import static com.kuador.medical.identity.Permission.ROLES_UPDATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kuador.medical.identity.Permission;
import com.kuador.medical.identity.PermissionScope;
import com.kuador.medical.identity.role.EscalationGuard.Actor;
import com.kuador.medical.identity.role.EscalationGuard.GuardedRole;
import com.kuador.medical.identity.role.EscalationGuard.Slot;
import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

/**
 * Anti-escalation rules (rule 1 of the plan of MEDK-12), written before the implementation:
 * superset of permissions in the target branch, no self-assignment, only OWNER touches OWNER,
 * the last OWNER is kept and only OWNER edits ADMIN.
 */
class EscalationGuardTest {

    private static final UUID MAIN = UUID.randomUUID();
    private static final UUID NORTH = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID TARGET = UUID.randomUUID();

    private static final Set<Permission> ALL = Set.copyOf(Permission.ofScope(PermissionScope.TENANT));
    private static final GuardedRole OWNER = new GuardedRole(UUID.randomUUID(), SystemRole.OWNER, ALL);
    private static final GuardedRole ADMIN = new GuardedRole(UUID.randomUUID(), SystemRole.ADMIN, ALL);
    private static final GuardedRole RECEPTION =
            new GuardedRole(UUID.randomUUID(), null, Set.of(PATIENTS_READ, PATIENTS_REGISTER, PATIENTS_EDIT));
    private static final GuardedRole DOCTOR = new GuardedRole(UUID.randomUUID(), null, Set.of(PATIENTS_READ));
    private static final GuardedRole MANAGER = new GuardedRole(
            UUID.randomUUID(), null, Set.of(ASSIGNMENTS_MANAGE, PATIENTS_READ, PATIENTS_REGISTER, PATIENTS_EDIT));

    /** ADMIN in the main branch and doctor in the north branch. */
    private static final Actor ADMIN_IN_MAIN_DOCTOR_IN_NORTH =
            actor(false, Map.of(MAIN, ADMIN.permissions(), NORTH, DOCTOR.permissions()), ADMIN.permissions());
    private static final Actor OWNER_ACTOR = actor(true, Map.of(), ALL);
    private static final Actor MANAGER_IN_MAIN = actor(false, Map.of(MAIN, MANAGER.permissions()), MANAGER.permissions());

    static Stream<Arguments> assignments() {
        return Stream.of(
                // actor, current slots of the target, requested slots, active owners, expected error (null: allowed)
                Arguments.of("ADMIN assigns reception where it is ADMIN", ADMIN_IN_MAIN_DOCTOR_IN_NORTH,
                        List.of(), List.of(slot(MAIN, RECEPTION)), 1L, null),
                Arguments.of("ADMIN of main cannot assign reception in north, where it is only doctor",
                        ADMIN_IN_MAIN_DOCTOR_IN_NORTH, List.of(), List.of(slot(NORTH, RECEPTION)), 1L,
                        ErrorCode.PRIVILEGE_ESCALATION),
                Arguments.of("doctor permissions in north are enough to assign doctor there",
                        ADMIN_IN_MAIN_DOCTOR_IN_NORTH, List.of(), List.of(slot(NORTH, DOCTOR)), 1L, null),
                Arguments.of("manager cannot assign ADMIN, which has more permissions", MANAGER_IN_MAIN,
                        List.of(), List.of(slot(MAIN, ADMIN)), 1L, ErrorCode.PRIVILEGE_ESCALATION),
                Arguments.of("manager cannot remove an ADMIN either", MANAGER_IN_MAIN,
                        List.of(slot(MAIN, ADMIN)), List.of(slot(MAIN, RECEPTION)), 1L, ErrorCode.PRIVILEGE_ESCALATION),
                Arguments.of("unchanged assignments need no permission", MANAGER_IN_MAIN,
                        List.of(slot(NORTH, ADMIN)), List.of(slot(NORTH, ADMIN), slot(MAIN, RECEPTION)), 1L, null),
                Arguments.of("non-owner cannot assign OWNER", ADMIN_IN_MAIN_DOCTOR_IN_NORTH,
                        List.of(), List.of(slot(null, OWNER)), 1L, ErrorCode.PRIVILEGE_ESCALATION),
                Arguments.of("non-owner cannot remove OWNER", ADMIN_IN_MAIN_DOCTOR_IN_NORTH,
                        List.of(slot(null, OWNER)), List.of(slot(MAIN, RECEPTION)), 2L, ErrorCode.PRIVILEGE_ESCALATION),
                Arguments.of("owner assigns OWNER", OWNER_ACTOR, List.of(), List.of(slot(null, OWNER)), 1L, null),
                Arguments.of("owner removes another OWNER", OWNER_ACTOR,
                        List.of(slot(null, OWNER)), List.of(slot(MAIN, ADMIN)), 2L, null),
                Arguments.of("the last OWNER cannot lose the role", OWNER_ACTOR,
                        List.of(slot(null, OWNER)), List.of(slot(MAIN, ADMIN)), 1L, ErrorCode.LAST_OWNER),
                Arguments.of("owner assigns anything in any branch", OWNER_ACTOR,
                        List.of(), List.of(slot(MAIN, ADMIN), slot(NORTH, RECEPTION)), 1L, null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("assignments")
    void assignmentRules(
            String scenario,
            Actor actor,
            List<Slot> current,
            List<Slot> requested,
            long activeOwners,
            @Nullable ErrorCode expected) {
        var check = assertThatCode(() ->
                EscalationGuard.checkAssignmentReplacement(actor, TARGET, current, requested, activeOwners));
        if (expected == null) {
            check.doesNotThrowAnyException();
        } else {
            check.isInstanceOfSatisfying(ApiException.class, error -> {
                assertThat(error.code()).isEqualTo(expected);
                assertThat(error.status())
                        .isEqualTo(expected == ErrorCode.PRIVILEGE_ESCALATION ? HttpStatus.FORBIDDEN : HttpStatus.CONFLICT);
            });
        }
    }

    @Test
    void nobodyChangesTheirOwnAssignmentsNotEvenAnOwner() {
        assertRejected(() -> EscalationGuard.checkAssignmentReplacement(
                OWNER_ACTOR, ACTOR, List.of(slot(null, OWNER)), List.of(slot(null, OWNER)), 2),
                ErrorCode.SELF_MODIFICATION_FORBIDDEN);
        assertRejected(() -> EscalationGuard.checkAssignmentReplacement(
                MANAGER_IN_MAIN, ACTOR, List.of(), List.of(slot(MAIN, DOCTOR)), 1),
                ErrorCode.SELF_MODIFICATION_FORBIDDEN);
    }

    @Test
    void aNewRoleOnlyCarriesPermissionsTheActorHolds() {
        assertThatCode(() -> EscalationGuard.checkRoleCreation(MANAGER_IN_MAIN, Set.of(PATIENTS_READ)))
                .doesNotThrowAnyException();
        assertRejected(() -> EscalationGuard.checkRoleCreation(MANAGER_IN_MAIN, Set.of(PATIENTS_READ, ROLES_UPDATE)),
                ErrorCode.PRIVILEGE_ESCALATION);
    }

    @Test
    void onlyAnOwnerEditsAdmin() {
        assertRejected(() -> EscalationGuard.checkRoleUpdate(
                ADMIN_IN_MAIN_DOCTOR_IN_NORTH, ADMIN, ADMIN.permissions(), false, Set.of()),
                ErrorCode.PRIVILEGE_ESCALATION);
        assertThatCode(() -> EscalationGuard.checkRoleUpdate(OWNER_ACTOR, ADMIN, Set.of(ROLES_READ), false, Set.of(MAIN)))
                .doesNotThrowAnyException();
    }

    @Test
    void aRoleAssignedToTheActorCannotBeEditedByIt() {
        assertRejected(() -> EscalationGuard.checkRoleUpdate(
                MANAGER_IN_MAIN, MANAGER, MANAGER.permissions(), true, Set.of(MAIN)),
                ErrorCode.SELF_MODIFICATION_FORBIDDEN);
    }

    @Test
    void addedPermissionsMustBeHeldInEveryBranchWhereTheRoleIsAssigned() {
        Set<Permission> doctorPlusRegister = Set.of(PATIENTS_READ, PATIENTS_REGISTER);

        assertThatCode(() -> EscalationGuard.checkRoleUpdate(
                ADMIN_IN_MAIN_DOCTOR_IN_NORTH, DOCTOR, doctorPlusRegister, false, Set.of(MAIN)))
                .doesNotThrowAnyException();
        assertRejected(() -> EscalationGuard.checkRoleUpdate(
                ADMIN_IN_MAIN_DOCTOR_IN_NORTH, DOCTOR, doctorPlusRegister, false, Set.of(MAIN, NORTH)),
                ErrorCode.PRIVILEGE_ESCALATION);
        assertRejected(() -> EscalationGuard.checkRoleUpdate(
                MANAGER_IN_MAIN, DOCTOR, Set.of(PATIENTS_READ, ROLES_UPDATE), false, Set.of()),
                ErrorCode.PRIVILEGE_ESCALATION);
    }

    @Test
    void removingPermissionsFromARoleNeedsNoExtraPermission() {
        assertThatCode(() -> EscalationGuard.checkRoleUpdate(
                MANAGER_IN_MAIN, RECEPTION, Set.of(PATIENTS_READ), false, Set.of(NORTH)))
                .doesNotThrowAnyException();
    }

    private static Actor actor(boolean owner, Map<UUID, Set<Permission>> byBranch, Set<Permission> current) {
        Map<UUID, Set<Permission>> copy = new HashMap<>(byBranch);
        return new Actor(ACTOR, owner, branchId -> {
            if (owner) {
                return ALL;
            }
            return branchId == null ? Set.of() : copy.getOrDefault(branchId, Set.of());
        }, current);
    }

    private static Slot slot(@Nullable UUID branchId, GuardedRole role) {
        return new Slot(branchId, role);
    }

    private static void assertRejected(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.code()).isEqualTo(code));
    }
}
