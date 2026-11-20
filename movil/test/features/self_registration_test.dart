import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../support/fake_api.dart';

Future<void> pickBirthDate(WidgetTester tester, String typed) async {
  final field = find.widgetWithText(TextFormField, 'Fecha de nacimiento');
  await tester.ensureVisible(field);
  await tester.tap(field);
  await tester.pumpAndSettle();
  await tester.tap(find.byIcon(Icons.edit_outlined));
  await tester.pumpAndSettle();
  await tester.enterText(find.byType(TextField).last, typed);
  await tester.tap(find.text('OK'));
  await tester.pumpAndSettle();
}

Future<void> selectOption(
  WidgetTester tester,
  String field,
  String option,
) async {
  // A select field opens a bottom sheet with the options (MEDK-45).
  final select = find.widgetWithText(TextFormField, field);
  await tester.ensureVisible(select);
  await tester.tap(select);
  await tester.pumpAndSettle();
  await tester.tap(find.text(option).last);
  await tester.pumpAndSettle();
}

Future<void> fillForm(WidgetTester tester, {required String birthDate}) async {
  await tapText(tester, 'Soy paciente y quiero registrarme');
  await enterText(tester, 'Código de la clínica', 'example-clinic');
  await selectOption(tester, 'Tipo de documento', 'Cédula de identidad');
  await enterText(tester, 'Número de documento', '2222222222');
  await enterText(tester, 'Nombres', 'Alex');
  await enterText(tester, 'Apellidos', 'Poe');
  await pickBirthDate(tester, birthDate);
  await selectOption(tester, 'Sexo', 'Femenino');
  await enterText(tester, 'Correo', 'alex.poe@example.com');
  await enterText(tester, 'Contraseña', 'a-long-example-passphrase');
}

void main() {
  const route = 'POST /public/clinics/example-clinic/patient-registrations';

  testWidgets('an adult self-registers with consent and an idempotency key', (
    tester,
  ) async {
    final api = FakeApi()..on(route, (_) => json(null, 202));
    await pumpApp(tester, api: api);

    await fillForm(tester, birthDate: '03/15/1990');
    await tapText(tester, 'Registrarme');
    expect(
      find.text(
        'Para registrarte debes aceptar el tratamiento de tus datos personales.',
      ),
      findsOneWidget,
    );
    expect(api.calls(route), isEmpty);

    await tapText(
      tester,
      'Acepto el tratamiento de mis datos personales por la clínica.',
    );
    await tapText(tester, 'Registrarme');

    final call = api.calls(route).single;
    final body = call.body as Map<String, dynamic>;
    expect(body, {
      'id': body['id'],
      'documentType': 'NATIONAL_ID',
      'documentNumber': '2222222222',
      'firstNames': 'Alex',
      'lastNames': 'Poe',
      'birthDate': '1990-03-15',
      'sex': 'FEMALE',
      'email': 'alex.poe@example.com',
      'password': 'a-long-example-passphrase',
      'consent': {'textVersion': '2026-01'},
    });
    expect(call.request.headers['Idempotency-Key'], body['id']);
    expect(find.textContaining('Recibimos tu registro'), findsOneWidget);
  });

  testWidgets('a minor cannot self-register', (tester) async {
    final api = FakeApi()..on(route, (_) => json(null, 202));
    await pumpApp(tester, api: api);

    // `now` is 2030-01-01: born 2012-06-01 is 17.
    await fillForm(tester, birthDate: '06/01/2012');
    await tapText(
      tester,
      'Acepto el tratamiento de mis datos personales por la clínica.',
    );
    await tapText(tester, 'Registrarme');

    expect(
      find.textContaining('Solo los adultos pueden registrarse solos'),
      findsOneWidget,
    );
    expect(api.calls(route), isEmpty);
  });

  testWidgets('a clinic without self-registration answers 404 in Spanish', (
    tester,
  ) async {
    final api = FakeApi(); // Unknown route: 404 NOT_FOUND.
    await pumpApp(tester, api: api);

    await fillForm(tester, birthDate: '03/15/1990');
    await tapText(
      tester,
      'Acepto el tratamiento de mis datos personales por la clínica.',
    );
    await tapText(tester, 'Registrarme');

    expect(
      find.text(
        'No encontramos esa clínica o no tiene habilitado el autorregistro.',
      ),
      findsOneWidget,
    );
  });

  testWidgets('server field errors are shown on their fields', (tester) async {
    final api = FakeApi()
      ..on(
        route,
        (_) => problem(
          'VALIDATION',
          400,
          errors: [
            {'field': 'documentNumber', 'code': 'INVALID_FORMAT'},
          ],
        ),
      );
    await pumpApp(tester, api: api);

    await fillForm(tester, birthDate: '03/15/1990');
    await tapText(
      tester,
      'Acepto el tratamiento de mis datos personales por la clínica.',
    );
    await tapText(tester, 'Registrarme');

    expect(
      find.text('Revisa los datos marcados e inténtalo de nuevo.'),
      findsOneWidget,
    );
    expect(find.text('El formato no es válido.'), findsOneWidget);
  });

  testWidgets('nothing is sent or stored without a connection', (tester) async {
    final api = FakeApi()..on(route, (_) => json(null, 202));
    final app = await pumpApp(tester, api: api);

    await fillForm(tester, birthDate: '03/15/1990');
    await tapText(
      tester,
      'Acepto el tratamiento de mis datos personales por la clínica.',
    );
    app.connectivity.online = false;
    await tester.pumpAndSettle();
    await tapText(tester, 'Registrarme');

    expect(find.textContaining('Sin conexión.'), findsOneWidget);
    expect(api.calls(route), isEmpty);
    expect(app.store.tokens, isNull);
  });
}
