import 'package:flutter_test/flutter_test.dart';

import '../support/fake_api.dart';
import '../support/module_fixtures.dart';

FakeApi patientsApi() => FakeApi()
  ..on('GET /patients', (_) => json(page([patient()])))
  ..on('GET /patients/$adultPatient', (_) => json(patient()))
  ..on('GET /patients/$adultPatient/guardian-links', (_) => json(<Object>[]))
  ..on(
    'GET /patients/$minorPatient',
    (_) =>
        json(patient(id: minorPatient, minor: true, birthDate: '2015-06-01')),
  )
  ..on(
    'GET /patients/$minorPatient/guardian-links',
    (_) => json([guardianLink()]),
  );

const consentLabel =
    'Se acepta el tratamiento de los datos personales del paciente.';
const guardianConsentLabel =
    'El representante acepta el tratamiento de los datos personales del paciente.';

Future<void> openRegisterForm(WidgetTester tester) async {
  await tapText(tester, 'Pacientes');
  await tapText(tester, 'Registrar');
}

Future<void> fillPatient(
  WidgetTester tester, {
  required String birthDate,
}) async {
  await selectDropdown(tester, 'Tipo de documento', 'Cédula de identidad');
  await enterText(tester, 'Número de documento', '2222222222');
  await enterText(tester, 'Nombres', 'Alex');
  await enterText(tester, 'Apellidos', 'Poe');
  await pickDate(tester, 'Elegir fecha de nacimiento', birthDate);
  await selectDropdown(tester, 'Sexo', 'Femenino');
}

void main() {
  testWidgets('registers an adult with consent and an Idempotency-Key', (
    tester,
  ) async {
    final api = patientsApi()
      ..on('POST /patients', (_) => json(patient(), 201));
    await pumpStaff(tester, api);
    await openRegisterForm(tester);
    await fillPatient(tester, birthDate: '03/15/1990');

    // Without consent the registration is not completed (FR-009).
    await tapText(tester, 'Registrar paciente');
    expect(
      find.text(
        'Sin el consentimiento de tratamiento de datos personales no se puede registrar al paciente.',
      ),
      findsOneWidget,
    );
    expect(api.calls('POST /patients'), isEmpty);

    await tapText(tester, consentLabel);
    await tapText(tester, 'Registrar paciente');

    final call = api.calls('POST /patients').single;
    final body = call.body as Map<String, dynamic>;
    expect(body, {
      'id': body['id'],
      'documentType': 'NATIONAL_ID',
      'documentNumber': '2222222222',
      'firstNames': 'Alex',
      'lastNames': 'Poe',
      'birthDate': '1990-03-15',
      'sex': 'FEMALE',
      'consent': {'textVersion': '2026-01', 'grantedBy': 'PATIENT'},
    });
    expect(call.request.headers['Idempotency-Key'], body['id']);
    // Opens the new patient.
    expect(find.text('Alex Poe'), findsOneWidget);
    expect(find.textContaining('versión 2026-01'), findsOneWidget);
  });

  testWidgets('a minor requires a legal guardian and the guardian consents', (
    tester,
  ) async {
    final api = patientsApi()
      ..on(
        'POST /patients',
        (_) => json(
          patient(id: minorPatient, minor: true, birthDate: '2015-06-01'),
          201,
        ),
      );
    await pumpStaff(tester, api);
    await openRegisterForm(tester);
    // `now` is 2030-01-01: born 2015-06-01 is 14.
    await fillPatient(tester, birthDate: '06/01/2015');

    expect(find.textContaining('Es menor de edad'), findsOneWidget);
    expect(find.text('Lo acepta el paciente'), findsNothing);
    await tapText(tester, guardianConsentLabel);
    await tapText(tester, 'Registrar paciente');
    expect(api.calls('POST /patients'), isEmpty, reason: 'guardian missing');

    await enterText(tester, 'Parentesco o relación', 'Madre');
    await selectDropdown(
      tester,
      'Tipo de documento del representante',
      'Cédula de identidad',
    );
    await enterText(
      tester,
      'Número de documento del representante',
      '1111111111',
    );
    await enterText(tester, 'Nombres del representante', 'Mary');
    await enterText(tester, 'Apellidos del representante', 'Roe');
    await enterText(
      tester,
      'Correo del representante (opcional)',
      'mary.roe@example.com',
    );
    await tapText(tester, 'Registrar paciente');

    final body = api.calls('POST /patients').single.body as Map;
    expect(body['consent'], {
      'textVersion': '2026-01',
      'grantedBy': 'GUARDIAN',
    });
    expect(body['guardian'], {
      'relationship': 'Madre',
      'person': {
        'documentType': 'NATIONAL_ID',
        'documentNumber': '1111111111',
        'firstNames': 'Mary',
        'lastNames': 'Roe',
        'email': 'mary.roe@example.com',
      },
    });
    expect(find.text('Menor de edad'), findsOneWidget);
    expect(find.textContaining('no tiene cuenta propia'), findsOneWidget);
  });

  testWidgets('a duplicate document and server field errors are shown', (
    tester,
  ) async {
    var answer = problem('PATIENT_DUPLICATE', 409);
    final api = patientsApi()..on('POST /patients', (_) => answer);
    await pumpStaff(tester, api);
    await openRegisterForm(tester);
    await fillPatient(tester, birthDate: '03/15/1990');
    await tapText(tester, consentLabel);

    await tapText(tester, 'Registrar paciente');
    expect(
      find.text(
        'Ya existe un paciente con ese tipo y número de documento en la clínica.',
      ),
      findsOneWidget,
    );

    answer = problem(
      'VALIDATION',
      400,
      errors: [
        {'field': 'documentNumber', 'code': 'INVALID_FORMAT'},
      ],
    );
    await tapText(tester, 'Registrar paciente');
    expect(find.text('El formato no es válido.'), findsOneWidget);
  });

  testWidgets('nothing is sent or stored without a connection', (tester) async {
    final api = patientsApi();
    final app = await pumpStaff(tester, api);
    await openRegisterForm(tester);
    await fillPatient(tester, birthDate: '03/15/1990');
    await tapText(tester, consentLabel);

    app.connectivity.online = false;
    await tester.pumpAndSettle();
    await tester.tap(find.text('Registrar paciente'));
    await tester.pumpAndSettle();

    expect(api.calls('POST /patients'), isEmpty);
    expect(find.textContaining('Sin conexión'), findsOneWidget);
  });

  testWidgets('reception without register permission only reads', (
    tester,
  ) async {
    await pumpStaff(
      tester,
      patientsApi(),
      permissions: const ['patients.read'],
      roles: const ['Médico'],
    );
    await tapText(tester, 'Pacientes');
    expect(find.text('Registrar'), findsNothing);

    await tapText(tester, 'Alex Poe');
    expect(find.text('Editar datos'), findsNothing);
    expect(find.text('Invitar a activar su cuenta'), findsNothing);
  });

  testWidgets('invites an adult patient to activate the account', (
    tester,
  ) async {
    final api = patientsApi()
      ..on(
        'POST /patients/$adultPatient/account-invitation',
        (_) => json(null, 202),
      );
    await pumpStaff(tester, api);
    await tapText(tester, 'Pacientes');
    await tapText(tester, 'Alex Poe');

    await tapText(tester, 'Invitar a activar su cuenta');
    await tapText(tester, 'Correo');

    expect(
      api.calls('POST /patients/$adultPatient/account-invitation').single.body,
      {'channel': 'EMAIL'},
    );
    expect(
      find.text('Se envió la invitación para activar la cuenta.'),
      findsOneWidget,
    );
  });

  testWidgets('records a missing consent with an Idempotency-Key', (
    tester,
  ) async {
    final api = patientsApi()
      ..on(
        'GET /patients/$adultPatient',
        (_) => json({...patient(), 'currentConsent': null}),
      )
      ..on('POST /patients/$adultPatient/consents', (_) => json({}, 201));
    await pumpStaff(tester, api);
    await tapText(tester, 'Pacientes');
    await tapText(tester, 'Alex Poe');

    expect(find.text('No hay un consentimiento vigente.'), findsOneWidget);
    await tapText(tester, 'Registrar consentimiento');
    await tapText(tester, 'Registrar');

    final call = api.calls('POST /patients/$adultPatient/consents').single;
    final body = call.body as Map;
    expect(body['textVersion'], '2026-01');
    expect(body['grantedBy'], 'PATIENT');
    expect(call.request.headers['Idempotency-Key'], body['id']);
  });

  testWidgets('the last legal guardian of a minor cannot be removed', (
    tester,
  ) async {
    final api = patientsApi()
      ..on(
        'GET /patients',
        (_) => json(page([patient(id: minorPatient, minor: true)])),
      )
      ..on(
        'POST /patients/$minorPatient/guardian-links/$linkMary/end',
        (_) => problem('LAST_LEGAL_GUARDIAN', 409),
      );
    await pumpStaff(tester, api);
    await tapText(tester, 'Pacientes');
    await tapText(tester, 'John Roe');

    expect(find.text('Mary Roe'), findsOneWidget);
    await tester.tap(find.byTooltip('Terminar vínculo con Mary Roe'));
    await tester.pumpAndSettle();
    await tapText(tester, 'Terminar');

    expect(
      find.text(
        'Un paciente menor de edad debe conservar al menos un representante legal.',
      ),
      findsOneWidget,
    );
  });

  testWidgets('adds a legal guardian to a minor', (tester) async {
    final api = patientsApi()
      ..on(
        'GET /patients',
        (_) => json(page([patient(id: minorPatient, minor: true)])),
      )
      ..on(
        'POST /patients/$minorPatient/guardian-links',
        (_) =>
            json(guardianLink(id: '7a6b5c4d-3e2f-4a1b-8c9d-0e1f2a3b4c5d'), 201),
      );
    await pumpStaff(tester, api);
    await tapText(tester, 'Pacientes');
    await tapText(tester, 'John Roe');
    await tapText(tester, 'Agregar representante legal');

    await enterText(tester, 'Parentesco o relación', 'Padre');
    await selectDropdown(
      tester,
      'Tipo de documento del representante',
      'Pasaporte',
    );
    await enterText(tester, 'Número de documento del representante', 'P000000');
    await enterText(tester, 'Nombres del representante', 'Rick');
    await enterText(tester, 'Apellidos del representante', 'Roe');
    await tapText(tester, 'Agregar representante');

    final call = api
        .calls('POST /patients/$minorPatient/guardian-links')
        .single;
    final body = call.body as Map;
    expect(body['relationship'], 'Padre');
    expect(body['person'], {
      'documentType': 'PASSPORT',
      'documentNumber': 'P000000',
      'firstNames': 'Rick',
      'lastNames': 'Roe',
    });
    expect(call.request.headers['Idempotency-Key'], body['id']);
  });

  testWidgets('edits patient data with only the changed fields', (
    tester,
  ) async {
    final api = patientsApi()
      ..on('PATCH /patients/$adultPatient', (_) => json(patient(version: 1)));
    await pumpStaff(tester, api);
    await tapText(tester, 'Pacientes');
    await tapText(tester, 'Alex Poe');
    await tapText(tester, 'Editar datos');

    await enterText(tester, 'Teléfono (opcional)', '+593000000000');
    await tapText(tester, 'Guardar cambios');

    expect(api.calls('PATCH /patients/$adultPatient').single.body, {
      'phone': '+593000000000',
      'version': 0,
    });
  });
}
