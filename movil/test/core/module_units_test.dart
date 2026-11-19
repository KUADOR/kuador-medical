import 'package:flutter_test/flutter_test.dart';
import 'package:medical_kuador/core/api/api_client.dart';
import 'package:medical_kuador/core/api/api_exception.dart';
import 'package:medical_kuador/core/api/error_messages.dart';
import 'package:medical_kuador/core/session/session_models.dart';
import 'package:medical_kuador/core/util/idempotent_creation.dart';
import 'package:medical_kuador/features/roles/data/role_models.dart';
import 'package:medical_kuador/features/staff_users/data/staff_models.dart';

import '../support/fake_api.dart';
import '../support/module_fixtures.dart';

const main$ = NamedRef(id: branchMain, name: 'Main branch');
const north = NamedRef(id: branchNorth, name: 'North branch');

RoleAssignment assignment(NamedRef? branch, String roleId, String name) =>
    RoleAssignment(
      branch: branch,
      role: NamedRef(id: roleId, name: name),
    );

void main() {
  final roles = Role.listFromJson(clinicRoles());

  group('AssignmentDraft', () {
    test('the same role in every branch reads as all branches', () {
      final draft = AssignmentDraft.fromAssignments(
        [
          assignment(main$, roleDoctor, 'Médico'),
          assignment(north, roleDoctor, 'Médico'),
        ],
        [main$, north],
      );
      expect(draft.mode, AssignmentMode.allBranches);
      expect(draft.toRequest(), {'mode': 'ALL_BRANCHES', 'roleId': roleDoctor});
    });

    test('different roles, or missing branches, read as per branch', () {
      final draft = AssignmentDraft.fromAssignments(
        [
          assignment(main$, roleAdmin, 'ADMIN'),
          assignment(north, roleDoctor, 'Médico'),
        ],
        [main$, north],
      );
      expect(draft.mode, AssignmentMode.perBranch);
      expect(draft.toRequest(), {
        'mode': 'PER_BRANCH',
        'assignments': [
          {'branchId': branchMain, 'roleId': roleAdmin},
          {'branchId': branchNorth, 'roleId': roleDoctor},
        ],
      });

      final partial = AssignmentDraft.fromAssignments(
        [assignment(main$, roleDoctor, 'Médico')],
        [main$, north],
      );
      expect(partial.mode, AssignmentMode.perBranch);
    });

    test('the clinic-wide OWNER assignment reads as all branches', () {
      final draft = AssignmentDraft.fromAssignments(
        [assignment(null, roleOwner, 'OWNER')],
        [main$, north],
      );
      expect(draft.toRequest(), {'mode': 'ALL_BRANCHES', 'roleId': roleOwner});
    });

    test('no role means no request, and "no access" removes a branch', () {
      expect(const AssignmentDraft().toRequest(), isNull);
      final draft = const AssignmentDraft(
        mode: AssignmentMode.perBranch,
      ).withBranchRole(branchMain, roleDoctor).withBranchRole(branchMain, null);
      expect(draft.toRequest(), isNull);
    });

    test('a clinical role requires the professional registration', () {
      expect(
        const AssignmentDraft(
          roleId: roleDoctor,
        ).requiresProfessionalRegistration(roles),
        isTrue,
      );
      expect(
        const AssignmentDraft(
          roleId: roleReception,
        ).requiresProfessionalRegistration(roles),
        isFalse,
      );
    });

    test('OWNER is offered only to an OWNER and only for all branches', () {
      List<String> names(bool owner, AssignmentMode mode) => [
        for (final r in assignableRoles(
          roles,
          callerIsOwner: owner,
          mode: mode,
        ))
          r.name,
      ];
      expect(names(true, AssignmentMode.allBranches), contains('OWNER'));
      expect(names(true, AssignmentMode.perBranch), isNot(contains('OWNER')));
      expect(
        names(false, AssignmentMode.allBranches),
        isNot(contains('OWNER')),
      );
    });

    test('editor branches keep the branches already assigned', () {
      const south = NamedRef(id: 'south', name: 'South branch');
      expect(
        editorBranches([main$], [assignment(south, roleDoctor, 'Médico')]),
        [main$, south],
      );
    });

    test('describes the assignments in Spanish', () {
      expect(describeAssignments(const []), 'Sin roles asignados');
      expect(
        describeAssignments([
          assignment(null, roleOwner, 'OWNER'),
          assignment(north, roleDoctor, 'Médico'),
        ]),
        'OWNER en todas las sucursales · Médico en North branch',
      );
    });
  });

  group('IdempotentCreation', () {
    test('reuses the id for the same data and renews it for new data', () {
      var n = 0;
      final creation = IdempotentCreation(newId: () => 'id-${n++}');
      expect(creation.idFor({'a': 1}), 'id-0');
      expect(creation.idFor({'a': 1}), 'id-0');
      expect(creation.idFor({'a': 2}), 'id-1');
      creation.completed();
      expect(creation.idFor({'a': 2}), 'id-2');
    });
  });

  group('ApiClient', () {
    test(
      'sends query parameters and the PUT, PATCH and DELETE verbs',
      () async {
        final api = FakeApi()
          ..on('GET /staff-users', (_) => json(page([])))
          ..on('PUT /roles/x', (_) => json({}))
          ..on('PATCH /patients/x', (_) => json({}))
          ..on('DELETE /roles/x', (_) => json(null, 204));
        final client = ApiClient(httpClient: api.client, baseUrl: baseUrl)
          ..accessTokenProvider = _Tokens();

        await client.get(
          '/staff-users',
          query: {'q': 'ana maría', 'status': ''},
        );
        await client.put('/roles/x', body: {'version': 1});
        await client.patch('/patients/x', body: {'version': 1});
        await client.delete('/roles/x');

        final get = api.calls('GET /staff-users').single.request.url;
        expect(get.queryParameters, {'q': 'ana maría'});
        for (final r in api.requests) {
          expect(r.bearer, 'token');
        }
        expect(api.calls('PUT /roles/x').single.body, {'version': 1});
        expect(api.calls('PATCH /patients/x').single.body, {'version': 1});
        expect(api.calls('DELETE /roles/x'), hasLength(1));
      },
    );
  });

  test('every error code of the contract has a Spanish message', () {
    const codes = [
      'VALIDATION',
      'EMAIL_TAKEN',
      'UNAUTHENTICATED',
      'FORBIDDEN',
      'PASSWORD_CHANGE_REQUIRED',
      'NOT_FOUND',
      'ROLE_NAME_TAKEN',
      'VERSION_CONFLICT',
      'SYSTEM_ROLE_IMMUTABLE',
      'ROLE_IN_USE',
      'INTERNAL_ERROR',
      'TOO_MANY_ATTEMPTS',
      'BRANCH_NOT_ASSIGNED',
      'PRIVILEGE_ESCALATION',
      'SELF_MODIFICATION_FORBIDDEN',
      'LAST_OWNER',
      'PLAN_LIMIT_REACHED',
      'IDEMPOTENCY_KEY_REUSED',
      'RESOURCE_ID_TAKEN',
      'ACCOUNT_ALREADY_ACTIVE',
      'PATIENT_DUPLICATE',
      'MINOR_CANNOT_HAVE_ACCOUNT',
      'ADULT_AUTHORIZATION_REQUIRED',
      'LAST_LEGAL_GUARDIAN',
      'CONTACT_CHANNEL_UNAVAILABLE',
    ];
    final fallback = apiErrorMessage(Object());
    for (final code in codes) {
      expect(
        apiErrorMessage(ApiException(code: code)),
        isNot(fallback),
        reason: code,
      );
    }
  });
}

class _Tokens implements AccessTokenProvider {
  @override
  Future<String?> validAccessToken() async => 'token';

  @override
  Future<String?> refreshAfterRejection(String rejectedToken) async => null;
}
