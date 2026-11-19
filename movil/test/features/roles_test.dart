import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../support/fake_api.dart';
import '../support/module_fixtures.dart';

FakeApi rolesApi() => FakeApi()
  ..on('GET /roles', (_) => json(clinicRoles()))
  ..on('GET /permissions', (_) => json(permissionCatalog()));

CheckboxListTile permissionTile(WidgetTester tester, String code) =>
    tester.widget<CheckboxListTile>(
      find.ancestor(
        of: find.text('Permiso $code'),
        matching: find.byType(CheckboxListTile),
      ),
    );

void main() {
  testWidgets('lists the clinic roles, marking protected and clinical ones', (
    tester,
  ) async {
    await pumpStaff(tester, rolesApi());
    await tapText(tester, 'Roles y permisos');

    expect(find.text('OWNER'), findsOneWidget);
    expect(find.text('Médico'), findsOneWidget);
    expect(find.textContaining('Protegido'), findsNWidgets(2));
    expect(find.text('Clínico · 1 permiso'), findsOneWidget);
  });

  testWidgets('creates a role only with held permissions', (tester) async {
    final api = rolesApi()
      ..on(
        'POST /roles',
        (r) => json(
          role(roleReception, 'Caja', permissions: ['patients.read']),
          201,
        ),
      );
    await pumpStaff(
      tester,
      api,
      permissions: const ['roles.read', 'roles.create', 'patients.read'],
      roles: const ['ADMIN'],
    );
    await tapText(tester, 'Roles y permisos');
    await tapText(tester, 'Nuevo rol');

    // Platform permissions are never listed; those the person lacks are locked.
    expect(find.text('Permiso de plataforma'), findsNothing);
    expect(permissionTile(tester, 'patients.register').onChanged, isNull);
    expect(permissionTile(tester, 'patients.read').onChanged, isNotNull);

    await enterText(tester, 'Nombre del rol', 'Caja');
    await tapText(tester, 'Permiso patients.read');
    await tapText(tester, 'Crear rol');

    final call = api.calls('POST /roles').single;
    final body = call.body as Map<String, dynamic>;
    expect(body, {
      'id': body['id'],
      'name': 'Caja',
      'description': null,
      'clinical': false,
      'permissions': ['patients.read'],
    });
    expect(call.request.headers['Idempotency-Key'], body['id']);
    expect(find.text('Se creó el rol.'), findsOneWidget);
  });

  testWidgets('OWNER is read-only and only an OWNER edits ADMIN', (
    tester,
  ) async {
    await pumpStaff(tester, rolesApi(), roles: const ['ADMIN']);
    await tapText(tester, 'Roles y permisos');

    await tapText(tester, 'OWNER');
    expect(find.textContaining('El rol OWNER está protegido'), findsOneWidget);
    expect(find.text('Guardar cambios'), findsNothing);
    expect(find.text('Eliminar rol'), findsNothing);
    await tester.pageBack();
    await tester.pumpAndSettle();

    await tapText(tester, 'ADMIN');
    expect(
      find.text('Solo una persona con el rol OWNER puede editar el rol ADMIN.'),
      findsOneWidget,
    );
  });

  testWidgets('updates a role with its version', (tester) async {
    final api = rolesApi()
      ..on(
        'PUT /roles/$roleReception',
        (_) => json(role(roleReception, 'Recepción', version: 3)),
      );
    await pumpStaff(tester, api);
    await tapText(tester, 'Roles y permisos');
    await tapText(tester, 'Recepción');

    await tapText(tester, 'Permiso patients.edit');
    await tapText(tester, 'Guardar cambios');

    expect(api.calls('PUT /roles/$roleReception').single.body, {
      'name': 'Recepción',
      'description': null,
      'clinical': false,
      'permissions': ['patients.edit', 'patients.read', 'patients.register'],
      'version': 2,
    });
  });

  testWidgets('a role in use cannot be deleted', (tester) async {
    final api = rolesApi()
      ..on('DELETE /roles/$roleReception', (_) => problem('ROLE_IN_USE', 409));
    await pumpStaff(tester, api);
    await tapText(tester, 'Roles y permisos');
    await tapText(tester, 'Recepción');

    await tapText(tester, 'Eliminar rol');
    await tapText(tester, 'Eliminar');

    expect(api.calls('DELETE /roles/$roleReception'), hasLength(1));
    expect(
      find.text(
        'No se puede eliminar el rol porque está asignado a personas de tu clínica.',
      ),
      findsOneWidget,
    );
  });
}
