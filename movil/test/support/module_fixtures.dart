import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'fake_api.dart';

/// Fictitious ids of the module tests.
const ownerAccountId = '0f1e2d3c-4b5a-4968-8776-655443322110';
const roleOwner = '7c9e6679-7425-40de-944b-e07fc1f90ae7';
const roleAdmin = '1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d';
const roleDoctor = '4b3a2c1d-0e9f-4a8b-8c7d-6e5f4a3b2c1d';
const roleReception = '9f8e7d6c-5b4a-4392-8a1b-0c9d8e7f6a5b';
const staffJane = '1d2c3b4a-5f6e-4789-8a9b-0c1d2e3f4a5b';
const adultPatient = '2b3c4d5e-6f70-4812-9a3b-4c5d6e7f8091';
const minorPatient = '6a5b4c3d-2e1f-4a0b-9c8d-7e6f5a4b3c2d';
const guardianMary = '5e4d3c2b-1a09-4f8e-8d7c-6b5a4f3e2d1c';
const linkMary = '3b2a1c0d-9e8f-4a7b-8c6d-5e4f3a2b1c0d';

const allStaffPermissions = [
  'roles.read',
  'roles.create',
  'roles.update',
  'roles.delete',
  'identity.users.read',
  'identity.users.manage',
  'identity.assignments.manage',
  'patients.read',
  'patients.register',
  'patients.edit',
  'patients.guardians.manage',
  'patients.selfRegistration.configure',
];

Map<String, Object?> role(
  String id,
  String name, {
  String? systemKey,
  bool clinical = false,
  List<String> permissions = const [],
  int version = 0,
}) => {
  'id': id,
  'name': name,
  'description': null,
  'systemKey': systemKey,
  'systemRole': systemKey != null,
  'clinical': clinical,
  'permissions': permissions,
  'version': version,
};

List<Map<String, Object?>> clinicRoles() => [
  role(
    roleOwner,
    'OWNER',
    systemKey: 'OWNER',
    permissions: allStaffPermissions,
  ),
  role(
    roleAdmin,
    'ADMIN',
    systemKey: 'ADMIN',
    permissions: allStaffPermissions,
  ),
  role(roleDoctor, 'Médico', clinical: true, permissions: ['patients.read']),
  role(
    roleReception,
    'Recepción',
    permissions: ['patients.read', 'patients.register'],
    version: 2,
  ),
];

List<Map<String, Object?>> permissionCatalog() => [
  for (final code in allStaffPermissions)
    {
      'code': code,
      'module': code.startsWith('patients') ? 'patients' : 'identity',
      'scope': 'TENANT',
      'description': 'Permiso $code',
    },
  {
    'code': 'platform.permissions.read',
    'module': 'platform',
    'scope': 'PLATFORM',
    'description': 'Permiso de plataforma',
  },
];

Map<String, Object?> staffUser({
  String id = staffJane,
  String status = 'INVITED',
  String? professionalRegistration,
  List<Map<String, Object?>>? assignments,
  int version = 0,
}) => {
  'id': id,
  'firstNames': 'Jane',
  'lastNames': 'Doe',
  'email': 'jane.doe@example.com',
  'jobTitle': 'Recepción',
  'professionalRegistration': professionalRegistration,
  'status': status,
  'roleAssignments':
      assignments ??
      [
        {
          'branch': {'id': branchMain, 'name': 'Main branch'},
          'role': {'id': roleReception, 'name': 'Recepción'},
        },
        {
          'branch': {'id': branchNorth, 'name': 'North branch'},
          'role': {'id': roleReception, 'name': 'Recepción'},
        },
      ],
  'invitationExpiresAt': status == 'INVITED' ? '2030-01-04T15:00:00Z' : null,
  'createdAt': '2030-01-01T15:00:00Z',
  'version': version,
};

Map<String, Object?> page(List<Object?> items) => {
  'page': 0,
  'size': 20,
  'totalItems': items.length,
  'items': items,
};

Map<String, Object?> patient({
  String id = adultPatient,
  bool minor = false,
  String birthDate = '1990-03-15',
  String accountStatus = 'NONE',
  String? email = 'alex.poe@example.com',
  Map<String, Object?>? consent,
  int version = 0,
}) => {
  'id': id,
  'documentType': 'NATIONAL_ID',
  'documentNumber': '2222222222',
  'firstNames': minor ? 'John' : 'Alex',
  'lastNames': minor ? 'Roe' : 'Poe',
  'birthDate': birthDate,
  'sex': 'FEMALE',
  'email': email,
  'phone': null,
  'address': null,
  'minor': minor,
  'adultSince': null,
  'origin': 'STAFF',
  'accountStatus': accountStatus,
  'currentConsent':
      consent ??
      {
        'id': '4d3c2b1a-0f9e-4d8c-9b7a-6f5e4d3c2b1a',
        'textVersion': '2026-01',
        'grantedAt': '2030-01-01T15:00:00Z',
        'grantedBy': minor ? 'GUARDIAN' : 'PATIENT',
        'channel': 'MOBILE',
        'revokedAt': null,
      },
  'createdAt': '2030-01-01T15:00:00Z',
  'version': version,
};

Map<String, Object?> guardianLink({
  String id = linkMary,
  String linkType = 'LEGAL_GUARDIAN',
  bool current = true,
  String? endReason,
}) => {
  'id': id,
  'guardian': {'id': guardianMary, 'firstNames': 'Mary', 'lastNames': 'Roe'},
  'linkType': linkType,
  'relationship': 'Madre',
  'validFrom': '2029-01-01T15:00:00Z',
  'validTo': current ? null : '2029-12-31T05:00:00Z',
  'endReason': endReason,
  'current': current,
  'authorizedBy': null,
};

/// Starts the app signed in as staff with [permissions] and the given roles.
Future<TestApp> pumpStaff(
  WidgetTester tester,
  FakeApi api, {
  List<String> permissions = allStaffPermissions,
  List<String> roles = const ['OWNER'],
  bool online = true,
}) async {
  useTallScreen(tester);
  api.on(
    'GET /me',
    (_) => json(staffMe(permissions: permissions, roles: roles)),
  );
  return pumpApp(tester, api: api, stored: storedTokens(), online: online);
}

/// Long forms fit without scrolling, so every field is built.
void useTallScreen(WidgetTester tester) {
  tester.view.physicalSize = const Size(800, 4000);
  tester.view.devicePixelRatio = 1.0;
  addTearDown(tester.view.reset);
}

Future<void> selectDropdown(
  WidgetTester tester,
  String label,
  String option, {
  int index = 0,
}) async {
  final dropdown = find
      .ancestor(
        of: find.text(label),
        matching: find.byWidgetPredicate((w) => w is DropdownButtonFormField),
      )
      .at(index);
  await tester.tap(dropdown);
  await tester.pumpAndSettle();
  await tester.tap(find.text(option).last);
  await tester.pumpAndSettle();
}

Future<void> pickDate(WidgetTester tester, String tooltip, String typed) async {
  await tester.tap(find.byTooltip(tooltip));
  await tester.pumpAndSettle();
  await tester.tap(find.byIcon(Icons.edit_outlined));
  await tester.pumpAndSettle();
  await tester.enterText(find.byType(TextField).last, typed);
  await tester.tap(find.text('OK'));
  await tester.pumpAndSettle();
}
