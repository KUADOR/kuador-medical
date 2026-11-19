import '../../../core/api/api_client.dart';
import '../../../core/api/api_exception.dart';
import '../../../core/session/session_models.dart';
import '../../roles/data/role_models.dart';

/// `AccountStatus` of the contract, with its Spanish label.
enum AccountStatus {
  invited('INVITED', 'Invitación pendiente'),
  pendingEmailVerification(
    'PENDING_EMAIL_VERIFICATION',
    'Verificación de correo pendiente',
  ),
  active('ACTIVE', 'Activo'),
  disabled('DISABLED', 'Deshabilitado');

  const AccountStatus(this.code, this.label);

  final String code;
  final String label;

  static AccountStatus parse(Object? value) => AccountStatus.values.firstWhere(
    (s) => s.code == value,
    orElse: () =>
        throw const ApiException(code: ApiException.invalidResponseCode),
  );
}

/// `RoleAssignment` of the contract. [branch] is null only for the
/// clinic-wide OWNER assignment.
class RoleAssignment {
  const RoleAssignment({required this.branch, required this.role});

  final NamedRef? branch;
  final NamedRef role;

  factory RoleAssignment.fromJson(Object? json) {
    final map = json is Map<String, dynamic>
        ? json
        : throw const ApiException(code: ApiException.invalidResponseCode);
    final branch = map['branch'];
    return RoleAssignment(
      branch: branch == null ? null : NamedRef.fromJson(branch),
      role: NamedRef.fromJson(map['role']),
    );
  }

  static List<RoleAssignment> listFromJson(Object? json) {
    if (json is! List) {
      throw const ApiException(code: ApiException.invalidResponseCode);
    }
    return [for (final item in json) RoleAssignment.fromJson(item)];
  }
}

/// `StaffUser` of the contract.
class StaffUser {
  const StaffUser({
    required this.id,
    required this.firstNames,
    required this.lastNames,
    required this.email,
    required this.jobTitle,
    required this.status,
    required this.roleAssignments,
    required this.version,
    this.professionalRegistration,
    this.invitationExpiresAt,
  });

  final String id;
  final String firstNames;
  final String lastNames;
  final String email;
  final String jobTitle;
  final String? professionalRegistration;
  final AccountStatus status;
  final List<RoleAssignment> roleAssignments;
  final DateTime? invitationExpiresAt;
  final int version;

  String get fullName => '$firstNames $lastNames'.trim();

  factory StaffUser.fromJson(Object? json) {
    final map = json is Map<String, dynamic>
        ? json
        : throw const ApiException(code: ApiException.invalidResponseCode);
    final expires = optionalField<String>(map, 'invitationExpiresAt');
    return StaffUser(
      id: requireField<String>(map, 'id'),
      firstNames: requireField<String>(map, 'firstNames'),
      lastNames: requireField<String>(map, 'lastNames'),
      email: requireField<String>(map, 'email'),
      jobTitle: requireField<String>(map, 'jobTitle'),
      professionalRegistration: optionalField<String>(
        map,
        'professionalRegistration',
      ),
      status: AccountStatus.parse(map['status']),
      roleAssignments: RoleAssignment.listFromJson(map['roleAssignments']),
      invitationExpiresAt: expires == null ? null : DateTime.tryParse(expires),
      version: requireField<int>(map, 'version'),
    );
  }
}

/// Body of `POST /staff-users` (without `id`).
class StaffUserDraft {
  const StaffUserDraft({
    required this.firstNames,
    required this.lastNames,
    required this.email,
    required this.jobTitle,
    required this.assignments,
    this.professionalRegistration,
  });

  final String firstNames;
  final String lastNames;
  final String email;
  final String jobTitle;
  final String? professionalRegistration;
  final Map<String, Object?> assignments;

  Map<String, Object?> toJson() => {
    'firstNames': firstNames,
    'lastNames': lastNames,
    'email': email,
    'jobTitle': jobTitle,
    if (professionalRegistration != null &&
        professionalRegistration!.isNotEmpty)
      'professionalRegistration': professionalRegistration,
    'roleAssignments': assignments,
  };
}

// ----------------------------------------------------------------- Assignments

enum AssignmentMode { allBranches, perBranch }

/// What the person chooses in the assignments editor (user + branch + role,
/// ADR-013): the same role in every branch (default) or a role, or none, per
/// branch. Same rules as the web editor.
class AssignmentDraft {
  const AssignmentDraft({
    this.mode = AssignmentMode.allBranches,
    this.roleId,
    this.byBranch = const {},
  });

  final AssignmentMode mode;

  /// Role of the all-branches mode; null while none is chosen.
  final String? roleId;

  /// Role per branch id in the per-branch mode; absent means no access there.
  final Map<String, String> byBranch;

  AssignmentDraft copyWith({
    AssignmentMode? mode,
    String? roleId,
    Map<String, String>? byBranch,
  }) => AssignmentDraft(
    mode: mode ?? this.mode,
    roleId: roleId ?? this.roleId,
    byBranch: byBranch ?? this.byBranch,
  );

  /// Sets or clears (null) the role of one branch.
  AssignmentDraft withBranchRole(String branchId, String? roleId) {
    final next = Map<String, String>.of(byBranch);
    if (roleId == null) {
      next.remove(branchId);
    } else {
      next[branchId] = roleId;
    }
    return copyWith(byBranch: next);
  }

  /// `RoleAssignmentsRequest`, or null while no role is assigned.
  Map<String, Object?>? toRequest() {
    if (mode == AssignmentMode.allBranches) {
      return roleId == null ? null : {'mode': 'ALL_BRANCHES', 'roleId': roleId};
    }
    if (byBranch.isEmpty) return null;
    final ids = byBranch.keys.toList()..sort();
    return {
      'mode': 'PER_BRANCH',
      'assignments': [
        for (final id in ids) {'branchId': id, 'roleId': byBranch[id]},
      ],
    };
  }

  /// Ids of the roles this draft assigns.
  Set<String> get selectedRoleIds => switch (mode) {
    AssignmentMode.allBranches => {?roleId},
    AssignmentMode.perBranch => byBranch.values.toSet(),
  };

  /// True when any selected role is clinical (professional registration
  /// required, FR-007).
  bool requiresProfessionalRegistration(List<Role> roles) =>
      roles.any((r) => r.clinical && selectedRoleIds.contains(r.id));

  /// Reads the current assignments of a user. The same role in every listed
  /// branch, or the clinic-wide OWNER assignment, reads as all branches.
  static AssignmentDraft fromAssignments(
    List<RoleAssignment> assignments,
    List<NamedRef> branches,
  ) {
    if (assignments.isEmpty) return const AssignmentDraft();
    for (final a in assignments) {
      if (a.branch == null) return AssignmentDraft(roleId: a.role.id);
    }
    final byBranch = {for (final a in assignments) a.branch!.id: a.role.id};
    final roleIds = byBranch.values.toSet();
    final coversAll = branches.every((b) => byBranch.containsKey(b.id));
    if (roleIds.length == 1 && coversAll) {
      return AssignmentDraft(roleId: roleIds.single, byBranch: byBranch);
    }
    return AssignmentDraft(mode: AssignmentMode.perBranch, byBranch: byBranch);
  }
}

/// Roles offered in the editor. OWNER is clinic-wide: only an OWNER sees it,
/// and only for all branches. The API applies the same rules and the rest of
/// the anti-escalation checks.
List<Role> assignableRoles(
  List<Role> roles, {
  required bool callerIsOwner,
  required AssignmentMode mode,
}) => [
  for (final r in roles)
    if (!r.isOwner || (callerIsOwner && mode == AssignmentMode.allBranches)) r,
];

/// Branches shown in the editor: those of the caller's session plus those
/// where the user already has an assignment, so replacing the assignments
/// never drops a branch silently.
List<NamedRef> editorBranches(
  List<NamedRef> available, [
  List<RoleAssignment> assignments = const [],
]) {
  final branches = {for (final b in available) b.id: b};
  for (final a in assignments) {
    final branch = a.branch;
    if (branch != null) branches.putIfAbsent(branch.id, () => branch);
  }
  return branches.values.toList();
}

/// Short Spanish summary of the assignments of a user.
String describeAssignments(List<RoleAssignment> assignments) {
  if (assignments.isEmpty) return 'Sin roles asignados';
  return assignments
      .map(
        (a) => a.branch == null
            ? '${a.role.name} en todas las sucursales'
            : '${a.role.name} en ${a.branch!.name}',
      )
      .join(' · ');
}
