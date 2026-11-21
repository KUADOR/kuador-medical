package com.kuador.medical.identity.role;

import com.kuador.medical.identity.Permission;
import com.kuador.medical.identity.owner.LastOwnerGuard;
import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;

/**
 * Anti-escalation rules of roles and assignments (rule 1 of the plan of MEDK-12, Constitution II).
 * Pure domain logic: the caller's permissions per branch are given, nothing is read here.
 *
 * <ul>
 *   <li>Whoever assigns, removes or edits a role must hold, in the target branch, every permission
 *       of that role ({@code 403 PRIVILEGE_ESCALATION}).
 *   <li>Nobody changes their own assignments nor edits a role assigned to themselves
 *       ({@code 409 SELF_MODIFICATION_FORBIDDEN}).
 *   <li>Only an OWNER assigns or removes OWNER, and only an OWNER edits ADMIN
 *       ({@code 403 PRIVILEGE_ESCALATION}).
 *   <li>The last active OWNER cannot lose the role ({@code 409 LAST_OWNER}).
 * </ul>
 */
public final class EscalationGuard {

    private EscalationGuard() {}

    /**
     * Who is acting.
     *
     * @param permissionsIn permissions of the actor in a branch: its clinic-wide assignments plus
     *                      its assignment in that branch ({@code null}: clinic-wide only)
     * @param current       permissions of the actor in the request (its active context)
     */
    public record Actor(
            UUID userId,
            boolean owner,
            Function<@Nullable UUID, Set<Permission>> permissionsIn,
            Set<Permission> current) {

        public Actor {
            Objects.requireNonNull(userId, "userId");
            Objects.requireNonNull(permissionsIn, "permissionsIn");
            current = Set.copyOf(current);
        }
    }

    /** A role as the rules see it. */
    public record GuardedRole(UUID id, @Nullable SystemRole system, Set<Permission> permissions) {

        public GuardedRole {
            Objects.requireNonNull(id, "id");
            permissions = Set.copyOf(permissions);
        }

        boolean isOwner() {
            return system == SystemRole.OWNER;
        }
    }

    /** A role in a branch; {@code branchId} is null for the clinic-wide OWNER assignment. */
    public record Slot(@Nullable UUID branchId, GuardedRole role) {

        public Slot {
            Objects.requireNonNull(role, "role");
        }

        private boolean sameAs(Slot other) {
            return Objects.equals(branchId, other.branchId) && role.id().equals(other.role.id());
        }
    }

    /** A new role may only carry permissions the actor holds. */
    public static void checkRoleCreation(Actor actor, Set<Permission> requested) {
        if (!actor.current().containsAll(requested)) {
            throw escalation("The role would grant permissions the caller does not hold.");
        }
    }

    /**
     * Editing a role: ADMIN only by an OWNER, never a role assigned to the actor, and every
     * permission added must be held by the actor in its context and in every branch where the
     * role is assigned. OWNER itself is immutable ({@link Role#ensureUpdatable}).
     */
    public static void checkRoleUpdate(
            Actor actor,
            GuardedRole role,
            Set<Permission> requested,
            boolean assignedToActor,
            Set<@Nullable UUID> branchesWhereAssigned) {
        if (role.system() == SystemRole.ADMIN && !actor.owner()) {
            throw escalation("Only an OWNER can edit the ADMIN role.");
        }
        if (assignedToActor) {
            throw selfModification("A role assigned to the caller cannot be edited by the caller.");
        }
        Set<Permission> added = new HashSet<>(requested);
        added.removeAll(role.permissions());
        if (!actor.current().containsAll(added)) {
            throw escalation("The role would grant permissions the caller does not hold.");
        }
        for (UUID branchId : branchesWhereAssigned) {
            if (!actor.permissionsIn().apply(branchId).containsAll(added)) {
                throw escalation("The role is assigned where the caller does not hold the added permissions.");
            }
        }
    }

    /**
     * Replacing the assignments of {@code targetUserId}: {@code current} are its assignments
     * today, {@code requested} the ones it will have, {@code activeOwners} the active users of the
     * clinic holding OWNER (the target included).
     */
    public static void checkAssignmentReplacement(
            Actor actor, UUID targetUserId, List<Slot> current, List<Slot> requested, long activeOwners) {
        if (actor.userId().equals(targetUserId)) {
            throw selfModification("Nobody can change their own role assignments.");
        }
        boolean ownerNow = current.stream().anyMatch(slot -> slot.role().isOwner());
        boolean ownerAfter = requested.stream().anyMatch(slot -> slot.role().isOwner());
        if ((ownerNow || ownerAfter) && !actor.owner()) {
            throw escalation("Only an OWNER can assign or remove the OWNER role.");
        }
        if (ownerNow && !ownerAfter) {
            LastOwnerGuard.check(true, true, activeOwners);
        }
        for (Slot slot : changed(current, requested)) {
            if (!actor.permissionsIn().apply(slot.branchId()).containsAll(slot.role().permissions())) {
                throw escalation("The caller does not hold every permission of the role in that branch.");
            }
        }
    }

    /** Slots that are added or removed; unchanged ones need no check. */
    private static List<Slot> changed(List<Slot> current, List<Slot> requested) {
        List<Slot> added = requested.stream()
                .filter(slot -> current.stream().noneMatch(slot::sameAs))
                .toList();
        List<Slot> removed = current.stream()
                .filter(slot -> requested.stream().noneMatch(slot::sameAs))
                .toList();
        return Stream.concat(added.stream(), removed.stream()).toList();
    }

    private static ApiException escalation(String detail) {
        return new ApiException(HttpStatus.FORBIDDEN, ErrorCode.PRIVILEGE_ESCALATION, detail);
    }

    private static ApiException selfModification(String detail) {
        return ApiException.conflict(ErrorCode.SELF_MODIFICATION_FORBIDDEN, detail);
    }
}
