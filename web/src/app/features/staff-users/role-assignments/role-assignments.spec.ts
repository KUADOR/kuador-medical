import {
  ADMIN_ROLE,
  DOCTOR_ROLE,
  MAIN_BRANCH,
  NORTH_BRANCH,
  OWNER_ROLE,
  RECEPTION_ROLE,
} from '../../../../testing/fixtures';
import {
  EMPTY_DRAFT,
  assignableRoles,
  assignmentRequest,
  describeAssignments,
  draftFromAssignments,
  editorBranches,
  requiresProfessionalRegistration,
  selectedRoleIds,
} from './role-assignments';

const SOUTH_BRANCH = { id: '00000000-0000-0000-0000-0000000000b3', name: 'South branch' };
const doctor = { id: DOCTOR_ROLE.id, name: DOCTOR_ROLE.name };
const admin = { id: ADMIN_ROLE.id, name: ADMIN_ROLE.name };
const owner = { id: OWNER_ROLE.id, name: OWNER_ROLE.name };

describe('role assignments', () => {
  describe('draftFromAssignments', () => {
    it('starts empty, with the same role for every branch', () => {
      expect(draftFromAssignments([], [MAIN_BRANCH])).toEqual(EMPTY_DRAFT);
      expect(EMPTY_DRAFT.mode).toBe('ALL_BRANCHES');
    });

    it('reads the same role in every branch as ALL_BRANCHES', () => {
      const draft = draftFromAssignments(
        [
          { branch: MAIN_BRANCH, role: doctor },
          { branch: NORTH_BRANCH, role: doctor },
        ],
        [MAIN_BRANCH, NORTH_BRANCH],
      );
      expect(draft.mode).toBe('ALL_BRANCHES');
      expect(draft.roleId).toBe(DOCTOR_ROLE.id);
    });

    it('reads the clinic-wide OWNER assignment as ALL_BRANCHES', () => {
      const draft = draftFromAssignments([{ branch: null, role: owner }], [MAIN_BRANCH]);
      expect(draft).toEqual({ mode: 'ALL_BRANCHES', roleId: OWNER_ROLE.id, byBranch: {} });
    });

    it('reads different roles, or a missing branch, as PER_BRANCH', () => {
      const mixed = draftFromAssignments(
        [
          { branch: MAIN_BRANCH, role: admin },
          { branch: NORTH_BRANCH, role: doctor },
        ],
        [MAIN_BRANCH, NORTH_BRANCH],
      );
      expect(mixed).toEqual({
        mode: 'PER_BRANCH',
        roleId: '',
        byBranch: { [MAIN_BRANCH.id]: ADMIN_ROLE.id, [NORTH_BRANCH.id]: DOCTOR_ROLE.id },
      });

      const partial = draftFromAssignments(
        [{ branch: MAIN_BRANCH, role: doctor }],
        [MAIN_BRANCH, NORTH_BRANCH],
      );
      expect(partial.mode).toBe('PER_BRANCH');
    });
  });

  describe('assignmentRequest', () => {
    it('sends one role for all branches', () => {
      expect(assignmentRequest({ ...EMPTY_DRAFT, roleId: DOCTOR_ROLE.id })).toEqual({
        mode: 'ALL_BRANCHES',
        roleId: DOCTOR_ROLE.id,
      });
    });

    it('sends only the branches with a role in PER_BRANCH mode', () => {
      expect(
        assignmentRequest({
          mode: 'PER_BRANCH',
          roleId: '',
          byBranch: { [MAIN_BRANCH.id]: ADMIN_ROLE.id, [NORTH_BRANCH.id]: '' },
        }),
      ).toEqual({
        mode: 'PER_BRANCH',
        assignments: [{ branchId: MAIN_BRANCH.id, roleId: ADMIN_ROLE.id }],
      });
    });

    it('is null while no role is chosen', () => {
      expect(assignmentRequest(EMPTY_DRAFT)).toBeNull();
      expect(
        assignmentRequest({ mode: 'PER_BRANCH', roleId: '', byBranch: { [MAIN_BRANCH.id]: '' } }),
      ).toBeNull();
    });
  });

  it('requires the professional registration when any chosen role is clinical', () => {
    const roles = [RECEPTION_ROLE, DOCTOR_ROLE];
    const perBranch = {
      mode: 'PER_BRANCH' as const,
      roleId: '',
      byBranch: { [MAIN_BRANCH.id]: RECEPTION_ROLE.id, [NORTH_BRANCH.id]: DOCTOR_ROLE.id },
    };
    expect(requiresProfessionalRegistration(perBranch, roles)).toBe(true);
    expect(selectedRoleIds(perBranch)).toEqual([RECEPTION_ROLE.id, DOCTOR_ROLE.id]);
    expect(
      requiresProfessionalRegistration({ ...EMPTY_DRAFT, roleId: RECEPTION_ROLE.id }, roles),
    ).toBe(false);
  });

  it('offers OWNER only to an OWNER and only for all branches', () => {
    const roles = [OWNER_ROLE, ADMIN_ROLE, DOCTOR_ROLE];
    const names = (callerIsOwner: boolean, mode: 'ALL_BRANCHES' | 'PER_BRANCH') =>
      assignableRoles(roles, callerIsOwner, mode).map((role) => role.name);

    expect(names(true, 'ALL_BRANCHES')).toEqual(['OWNER', 'ADMIN', 'Médico']);
    expect(names(true, 'PER_BRANCH')).toEqual(['ADMIN', 'Médico']);
    expect(names(false, 'ALL_BRANCHES')).toEqual(['ADMIN', 'Médico']);
  });

  it('keeps the branches where the user already works in the editor', () => {
    expect(
      editorBranches([MAIN_BRANCH], [{ branch: SOUTH_BRANCH, role: doctor }]).map((b) => b.name),
    ).toEqual(['Main branch', 'South branch']);
    expect(editorBranches([MAIN_BRANCH, NORTH_BRANCH])).toHaveLength(2);
  });

  it('describes the assignments in Spanish', () => {
    expect(describeAssignments([])).toBe('Sin roles asignados');
    expect(
      describeAssignments([
        { branch: MAIN_BRANCH, role: admin },
        { branch: null, role: owner },
      ]),
    ).toBe('ADMIN en Main branch · OWNER en todas las sucursales');
  });
});
