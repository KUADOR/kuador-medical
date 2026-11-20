import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../support/fake_api.dart';
import '../support/module_fixtures.dart';

void main() {
  group('Adult patient links', () {
    Future<FakeApi> pumpPatient(WidgetTester tester) async {
      useTallScreen(tester);
      final api = FakeApi()
        ..on('GET /me', (_) => json(patientMe()))
        ..on(
          'GET /me/guardian-links',
          (_) => json([guardianLink(current: false, endReason: 'ADULTHOOD')]),
        )
        ..on(
          'POST /me/guardian-links',
          (_) => json(
            guardianLink(
              id: '7a6b5c4d-3e2f-4a1b-8c9d-0e1f2a3b4c5d',
              linkType: 'EMERGENCY_CONTACT',
            ),
            201,
          ),
        );
      await pumpApp(tester, api: api, stored: storedTokens());
      await tapText(tester, 'Mis contactos autorizados');
      return api;
    }

    testWidgets('shows the link ended at adulthood as history', (tester) async {
      await pumpPatient(tester);

      expect(find.text('Mary Roe'), findsOneWidget);
      expect(
        find.textContaining('Mayoría de edad del paciente'),
        findsOneWidget,
      );
    });

    testWidgets('authorizes the former guardian as emergency contact', (
      tester,
    ) async {
      final api = await pumpPatient(tester);

      // The former guardian is offered first, with the previous relationship.
      await tapText(tester, 'Autorizar');

      final call = api.calls('POST /me/guardian-links').single;
      final body = call.body as Map;
      expect(body, {
        'id': body['id'],
        'linkType': 'EMERGENCY_CONTACT',
        'relationship': 'Madre',
        'guardianId': guardianMary,
      });
      expect(call.request.headers['Idempotency-Key'], body['id']);
      expect(
        find.text('Autorizaste un contacto de emergencia.'),
        findsOneWidget,
      );
    });

    testWidgets('the guardian email field does not autocorrect', (
      tester,
    ) async {
      await pumpPatient(tester);
      await tapText(tester, 'Otra persona');

      final email = tester.widget<EditableText>(
        find.descendant(
          of: find.widgetWithText(
            TextFormField,
            'Correo del representante (opcional)',
          ),
          matching: find.byType(EditableText),
        ),
      );
      expect(email.autocorrect, isFalse);
      expect(email.keyboardType, TextInputType.emailAddress);
    });

    testWidgets('authorizes another person with delegated access', (
      tester,
    ) async {
      final api = await pumpPatient(tester);

      await tapText(tester, 'Otra persona');
      await tapText(tester, 'Acceso delegado');
      await enterText(tester, 'Parentesco o relación', 'Pareja');
      await selectDropdown(
        tester,
        'Tipo de documento del representante',
        'Cédula de identidad',
      );
      await enterText(
        tester,
        'Número de documento del representante',
        '3333333333',
      );
      await enterText(tester, 'Nombres del representante', 'Sam');
      await enterText(tester, 'Apellidos del representante', 'Poe');
      await tapText(tester, 'Autorizar');

      final body = api.calls('POST /me/guardian-links').single.body as Map;
      expect(body['linkType'], 'DELEGATED_ACCESS');
      expect(body['relationship'], 'Pareja');
      expect(body.containsKey('guardianId'), isFalse);
      expect(body['person'], {
        'documentType': 'NATIONAL_ID',
        'documentNumber': '3333333333',
        'firstNames': 'Sam',
        'lastNames': 'Poe',
      });
    });
  });

  group('Self-registration settings', () {
    testWidgets('enables self-registration with the current version', (
      tester,
    ) async {
      final api = FakeApi()
        ..on(
          'GET /patient-registration-settings',
          (_) => json({
            'selfRegistrationEnabled': false,
            'publicCode': 'example-clinic',
            'version': 4,
          }),
        )
        ..on(
          'PUT /patient-registration-settings',
          (_) => json({
            'selfRegistrationEnabled': true,
            'publicCode': 'example-clinic',
            'version': 5,
          }),
        );
      await pumpStaff(tester, api);
      await tapText(tester, 'Autorregistro de pacientes');

      expect(find.text('example-clinic'), findsOneWidget);
      await tapText(tester, 'Permitir el autorregistro de pacientes');

      expect(api.calls('PUT /patient-registration-settings').single.body, {
        'selfRegistrationEnabled': true,
        'version': 4,
      });
      expect(
        find.text('Se habilitó el autorregistro de pacientes.'),
        findsOneWidget,
      );
    });
  });
}
