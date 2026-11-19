import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../support/fake_api.dart';
import '../support/module_fixtures.dart';

FakeApi staffApi() => FakeApi()
  ..on('GET /staff-users', (_) => json(page([staffUser()])))
  ..on('GET /roles', (_) => json(clinicRoles()))
  ..on('GET /staff-users/$staffJane', (_) => json(staffUser()));

Future<void> openInviteForm(WidgetTester tester) async {
  await tapText(tester, 'Personal');
  await tapText(tester, 'Invitar');
}

Future<void> fillStaff(WidgetTester tester) async {
  await enterText(tester, 'Nombres', 'Jane');
  await enterText(tester, 'Apellidos', 'Doe');
  await enterText(tester, 'Correo', 'jane.doe@example.com');
  await enterText(tester, 'Descripción del puesto', 'Médico general');
}

void main() {
  testWidgets('lists the staff with their status and branch roles', (
    tester,
  ) async {
    final api = staffApi();
    await pumpStaff(tester, api);

    await tapText(tester, 'Personal');

    expect(find.text('Jane Doe'), findsOneWidget);
    expect(find.textContaining('Invitación pendiente'), findsOneWidget);
    expect(find.textContaining('Recepción en Main branch'), findsOneWidget);
    expect(api.calls('GET /staff-users').single.request.url.queryParameters, {
      'page': '0',
      'size': '20',
    });
  });

  testWidgets(
    'invites a clinical staff member for all branches with an Idempotency-Key',
    (tester) async {
      final api = staffApi()
        ..on('POST /staff-users', (_) => json(staffUser(), 201));
      await pumpStaff(tester, api);
      await openInviteForm(tester);
      await fillStaff(tester);
      await selectDropdown(tester, 'Rol', 'Médico (clínico)');

      // A clinical role requires the professional registration (FR-007).
      await tapText(tester, 'Registrar e invitar');
      expect(find.text('Es obligatorio para un rol clínico.'), findsOneWidget);
      expect(api.calls('POST /staff-users'), isEmpty);

      await enterText(tester, 'Registro profesional', 'REG-0000-EXAMPLE');
      await tapText(tester, 'Registrar e invitar');

      final call = api.calls('POST /staff-users').single;
      final body = call.body as Map<String, dynamic>;
      expect(body, {
        'id': body['id'],
        'firstNames': 'Jane',
        'lastNames': 'Doe',
        'email': 'jane.doe@example.com',
        'jobTitle': 'Médico general',
        'professionalRegistration': 'REG-0000-EXAMPLE',
        'roleAssignments': {'mode': 'ALL_BRANCHES', 'roleId': roleDoctor},
      });
      expect(body['id'], matches(RegExp(r'^[0-9a-f-]{36}$')));
      expect(call.request.headers['Idempotency-Key'], body['id']);
      expect(call.bearer, 'stored-access');
      expect(find.textContaining('se le envió la invitación'), findsOneWidget);
    },
  );

  testWidgets('assigns a different role per branch', (tester) async {
    final api = staffApi()
      ..on('POST /staff-users', (_) => json(staffUser(), 201));
    await pumpStaff(tester, api);
    await openInviteForm(tester);
    await fillStaff(tester);

    await tapText(tester, 'Un rol distinto por sucursal');
    await selectDropdown(tester, 'Rol en Main branch', 'ADMIN');
    await tapText(tester, 'Registrar e invitar');

    final body = api.calls('POST /staff-users').single.body as Map;
    expect(body['roleAssignments'], {
      'mode': 'PER_BRANCH',
      'assignments': [
        {'branchId': branchMain, 'roleId': roleAdmin},
      ],
    });
  });

  testWidgets('OWNER is offered only to an OWNER', (tester) async {
    final api = staffApi();
    await pumpStaff(tester, api, roles: const ['ADMIN']);
    await openInviteForm(tester);

    await tester.tap(
      find.ancestor(
        of: find.text('Rol'),
        matching: find.byWidgetPredicate((w) => w is DropdownButtonFormField),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('ADMIN'), findsWidgets);
    expect(find.text('OWNER'), findsNothing);
  });

  testWidgets('a role is required and API conflicts are shown in Spanish', (
    tester,
  ) async {
    final api = staffApi()
      ..on('POST /staff-users', (_) => problem('EMAIL_TAKEN', 409));
    await pumpStaff(tester, api);
    await openInviteForm(tester);
    await fillStaff(tester);

    await tapText(tester, 'Registrar e invitar');
    expect(
      find.text('Elige el rol de la persona en al menos una sucursal.'),
      findsOneWidget,
    );
    expect(api.calls('POST /staff-users'), isEmpty);

    await selectDropdown(tester, 'Rol', 'Recepción');
    await tapText(tester, 'Registrar e invitar');
    expect(
      find.text('Ya existe una cuenta con ese correo en la clínica.'),
      findsOneWidget,
    );

    // Retrying the same data reuses the same idempotency key.
    await tapText(tester, 'Registrar e invitar');
    final keys = [
      for (final c in api.calls('POST /staff-users'))
        c.request.headers['Idempotency-Key'],
    ];
    expect(keys, hasLength(2));
    expect(keys.toSet(), hasLength(1));
  });

  testWidgets('nothing is sent without a connection', (tester) async {
    final api = staffApi();
    final app = await pumpStaff(tester, api);
    await openInviteForm(tester);
    await fillStaff(tester);
    await selectDropdown(tester, 'Rol', 'Recepción');

    app.connectivity.online = false;
    await tester.pumpAndSettle();
    expect(find.textContaining('Sin conexión'), findsOneWidget);
    await tester.tap(find.text('Registrar e invitar'));
    await tester.pumpAndSettle();
    expect(api.calls('POST /staff-users'), isEmpty);
  });

  testWidgets('without manage permission there is no invite action', (
    tester,
  ) async {
    final api = staffApi();
    await pumpStaff(
      tester,
      api,
      permissions: const ['identity.users.read'],
      roles: const ['Auditor'],
    );
    await tapText(tester, 'Personal');
    expect(find.text('Invitar'), findsNothing);

    await tapText(tester, 'Jane Doe');
    expect(find.text('Deshabilitar acceso'), findsNothing);
    expect(find.text('Cambiar roles'), findsNothing);
  });

  testWidgets('resends the invitation and disables access after confirming', (
    tester,
  ) async {
    final api = staffApi()
      ..on('POST /staff-users/$staffJane/invitation', (_) => json(null, 202))
      ..on(
        'POST /staff-users/$staffJane/disable',
        (_) => json(staffUser(status: 'DISABLED')),
      );
    await pumpStaff(tester, api);
    await tapText(tester, 'Personal');
    await tapText(tester, 'Jane Doe');

    await tapText(tester, 'Reenviar invitación');
    expect(api.calls('POST /staff-users/$staffJane/invitation'), hasLength(1));
    expect(
      find.textContaining('Se envió una nueva invitación'),
      findsOneWidget,
    );

    await tapText(tester, 'Deshabilitar acceso');
    expect(
      find.textContaining('sus sesiones abiertas se cerrarán'),
      findsOneWidget,
    );
    await tapText(tester, 'Deshabilitar');
    expect(api.calls('POST /staff-users/$staffJane/disable'), hasLength(1));
    expect(find.text('Deshabilitado'), findsOneWidget);
    expect(find.text('Habilitar acceso'), findsOneWidget);
  });

  testWidgets('shows the last OWNER protection in Spanish', (tester) async {
    final api = staffApi()
      ..on(
        'GET /staff-users/$staffJane',
        (_) => json(staffUser(status: 'ACTIVE')),
      )
      ..on(
        'POST /staff-users/$staffJane/disable',
        (_) => problem('LAST_OWNER', 409),
      );
    await pumpStaff(tester, api);
    await tapText(tester, 'Personal');
    await tapText(tester, 'Jane Doe');
    await tapText(tester, 'Deshabilitar acceso');
    await tapText(tester, 'Deshabilitar');

    expect(
      find.text(
        'La clínica debe conservar al menos una persona con el rol OWNER.',
      ),
      findsOneWidget,
    );
  });

  testWidgets('nobody acts on their own record', (tester) async {
    final api = staffApi()
      ..on(
        'GET /staff-users',
        (_) => json(page([staffUser(id: ownerAccountId)])),
      )
      ..on(
        'GET /staff-users/$ownerAccountId',
        (_) => json(staffUser(id: ownerAccountId, status: 'ACTIVE')),
      );
    await pumpStaff(tester, api);
    await tapText(tester, 'Personal');
    await tapText(tester, 'Jane Doe');

    expect(find.textContaining('Esta es tu cuenta'), findsOneWidget);
    expect(find.text('Editar datos'), findsNothing);
    expect(find.text('Deshabilitar acceso'), findsNothing);
  });

  testWidgets('replaces the branch roles of a user', (tester) async {
    final api = staffApi()
      ..on(
        'PUT /staff-users/$staffJane/role-assignments',
        (_) => json([
          {
            'branch': {'id': branchMain, 'name': 'Main branch'},
            'role': {'id': roleAdmin, 'name': 'ADMIN'},
          },
        ]),
      );
    await pumpStaff(tester, api);
    await tapText(tester, 'Personal');
    await tapText(tester, 'Jane Doe');
    await tapText(tester, 'Cambiar roles');

    // Same role in every branch reads as "all branches".
    expect(
      tester
          .widget<RadioGroup<Object?>>(
            find.byWidgetPredicate((w) => w is RadioGroup),
          )
          .groupValue
          .toString(),
      contains('allBranches'),
    );
    await selectDropdown(tester, 'Rol', 'ADMIN');
    await tapText(tester, 'Guardar roles');

    expect(
      api.calls('PUT /staff-users/$staffJane/role-assignments').single.body,
      {'mode': 'ALL_BRANCHES', 'roleId': roleAdmin},
    );
    expect(find.textContaining('Se guardaron los roles'), findsOneWidget);
  });

  testWidgets('edits personal data with only the changed fields and version', (
    tester,
  ) async {
    final api = staffApi()
      ..on('PATCH /staff-users/$staffJane', (_) => json(staffUser(version: 1)));
    await pumpStaff(tester, api);
    await tapText(tester, 'Personal');
    await tapText(tester, 'Jane Doe');
    await tapText(tester, 'Editar datos');

    await enterText(tester, 'Descripción del puesto', 'Jefa de recepción');
    await tapText(tester, 'Guardar cambios');

    expect(api.calls('PATCH /staff-users/$staffJane').single.body, {
      'jobTitle': 'Jefa de recepción',
      'version': 0,
    });
  });
}
