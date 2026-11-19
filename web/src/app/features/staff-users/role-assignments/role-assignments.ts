import { isOwnerRole } from '../../../core/roles/roles.service';
import type {
  BranchSummary,
  Role,
  RoleAssignment,
  RoleAssignmentsRequest,
} from '../../../core/api/api-types';

export type AssignmentMode = RoleAssignmentsRequest['mode'];

/**
 * What the person is choosing in the assignments editor (user + branch + role, ADR-013):
 * one role for every branch, or a role (or none) per branch.
 */
export interface AssignmentDraft {
  mode: AssignmentMode;
  /** Role of the ALL_BRANCHES mode; '' while none is chosen. */
  roleId: string;
  /** Role per branch id in PER_BRANCH mode; '' or absent means no access in that branch. */
  byBranch: Readonly<Record<string, string>>;
}

export const EMPTY_DRAFT: AssignmentDraft = { mode: 'ALL_BRANCHES', roleId: '', byBranch: {} };

/**
 * Turns the current assignments of a user into a draft. Same role in every listed branch (or
 * the clinic-wide OWNER assignment) reads as ALL_BRANCHES; anything else as PER_BRANCH.
 */
export function draftFromAssignments(
  assignments: readonly RoleAssignment[],
  branches: readonly BranchSummary[],
): AssignmentDraft {
  if (assignments.length === 0) {
    return EMPTY_DRAFT;
  }
  const clinicWide = assignments.find((assignment) => !assignment.branch);
  if (clinicWide) {
    return { mode: 'ALL_BRANCHES', roleId: clinicWide.role.id, byBranch: {} };
  }
  const byBranch: Record<string, string> = {};
  for (const assignment of assignments) {
    if (assignment.branch) {
      byBranch[assignment.branch.id] = assignment.role.id;
    }
  }
  const roleIds = new Set(Object.values(byBranch));
  const coversEveryBranch = branches.every((branch) => byBranch[branch.id]);
  if (roleIds.size === 1 && coversEveryBranch) {
    return { mode: 'ALL_BRANCHES', roleId: [...roleIds][0], byBranch };
  }
  return { mode: 'PER_BRANCH', roleId: '', byBranch };
}

/** Request body for the draft, or null while it does not assign any role yet. */
export function assignmentRequest(draft: AssignmentDraft): RoleAssignmentsRequest | null {
  if (draft.mode === 'ALL_BRANCHES') {
    return draft.roleId ? { mode: 'ALL_BRANCHES', roleId: draft.roleId } : null;
  }
  const assignments = Object.entries(draft.byBranch)
    .filter(([, roleId]) => roleId)
    .map(([branchId, roleId]) => ({ branchId, roleId }));
  return assignments.length ? { mode: 'PER_BRANCH', assignments } : null;
}

/** Ids of the roles the draft assigns. */
export function selectedRoleIds(draft: AssignmentDraft): string[] {
  const request = assignmentRequest(draft);
  if (!request) {
    return [];
  }
  return request.mode === 'ALL_BRANCHES'
    ? [request.roleId]
    : [...new Set(request.assignments.map((assignment) => assignment.roleId))];
}

/** True when any role of the draft is clinical (the professional registration is required). */
export function requiresProfessionalRegistration(
  draft: AssignmentDraft,
  roles: readonly Role[],
): boolean {
  const selected = new Set(selectedRoleIds(draft));
  return roles.some((role) => role.clinical && selected.has(role.id));
}

/**
 * Roles offered in the editor. OWNER is clinic-wide: only an OWNER sees it, and only for all
 * branches. The API applies the same rules and the rest of anti-escalation (ADR-013).
 */
export function assignableRoles(
  roles: readonly Role[],
  callerIsOwner: boolean,
  mode: AssignmentMode,
): Role[] {
  return roles.filter((role) => !isOwnerRole(role) || (callerIsOwner && mode === 'ALL_BRANCHES'));
}

/**
 * Branches shown in the editor: those of the caller's session plus those where the user already
 * has an assignment, so replacing the assignments never drops a branch silently.
 */
export function editorBranches(
  available: readonly BranchSummary[],
  assignments: readonly RoleAssignment[] = [],
): BranchSummary[] {
  const branches = new Map(available.map((branch) => [branch.id, branch]));
  for (const assignment of assignments) {
    if (assignment.branch && !branches.has(assignment.branch.id)) {
      branches.set(assignment.branch.id, assignment.branch);
    }
  }
  return [...branches.values()];
}

/** Short Spanish summary of the assignments of a user, for lists. */
export function describeAssignments(assignments: readonly RoleAssignment[]): string {
  if (assignments.length === 0) {
    return 'Sin roles asignados';
  }
  return assignments
    .map((assignment) =>
      assignment.branch
        ? `${assignment.role.name} en ${assignment.branch.name}`
        : `${assignment.role.name} en todas las sucursales`,
    )
    .join(' · ');
}
