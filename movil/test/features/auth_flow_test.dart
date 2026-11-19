import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:medical_kuador/core/session/session_controller.dart';

import '../support/fake_api.dart';

const oneTimeToken = 'one-time-example-token-0123456789';

Future<void> login(WidgetTester tester) async {
  await enterText(tester, 'Correo', 'jane.doe@example.com');
  await enterText(tester, 'Contraseña', 'a-long-example-passphrase');
  await tapText(tester, 'Ingresar');
}

void main() {
  group('Login', () {
    testWidgets(
      'a single matching clinic goes straight to the home with the menu from /me',
      (tester) async {
        final api = FakeApi()
          ..on('POST /auth/login', (_) => json(authenticated()))
          ..on('GET /me', (_) => json(staffMe()));
        final app = await pumpApp(tester, api: api);

        expect(find.text('Iniciar sesión'), findsOneWidget);
        await login(tester);

        expect(api.calls('POST /auth/login').single.body, {
          'email': 'jane.doe@example.com',
          'password': 'a-long-example-passphrase',
        });
        expect(find.text('Hola, Jane Doe'), findsOneWidget);
        expect(find.text('Example Clinic'), findsOneWidget);
        expect(find.text('Main branch'), findsOneWidget);
        expect(find.text('Pacientes'), findsOneWidget);
        expect(find.text('Personal'), findsNothing);
        expect(app.store.tokens!.accessToken, 'access-1');
        expect(api.calls('GET /me').single.bearer, 'access-1');
      },
    );

    testWidgets('wrong credentials show the same generic message', (
      tester,
    ) async {
      final api = FakeApi()
        ..on('POST /auth/login', (_) => problem('INVALID_CREDENTIALS', 401));
      await pumpApp(tester, api: api);

      await login(tester);

      expect(
        find.text('El correo o la contraseña no son correctos.'),
        findsOneWidget,
      );
    });

    testWidgets('the temporary lockout is explained in Spanish', (
      tester,
    ) async {
      final api = FakeApi()
        ..on(
          'POST /auth/login',
          (_) => problem(
            'TOO_MANY_ATTEMPTS',
            429,
            headers: {'retry-after': '600'},
          ),
        );
      await pumpApp(tester, api: api);

      await login(tester);

      expect(
        find.textContaining(
          'Demasiados intentos. El acceso quedó bloqueado temporalmente',
        ),
        findsOneWidget,
      );
      expect(find.textContaining('10 minutos'), findsOneWidget);
    });

    testWidgets('several clinics: choose the clinic, then the branch', (
      tester,
    ) async {
      final api = FakeApi()
        ..on(
          'POST /auth/login',
          (_) => json({
            'status': 'CLINIC_SELECTION_REQUIRED',
            'preAuthToken': 'pre-auth-1',
            'preAuthExpiresIn': 300,
            'clinics': [
              {'id': clinicA, 'name': 'Example Clinic'},
              {'id': clinicB, 'name': 'Sample Dental'},
            ],
          }),
        )
        ..on(
          'POST /auth/login/clinic',
          (_) => json({
            'status': 'BRANCH_SELECTION_REQUIRED',
            'preAuthToken': 'pre-auth-2',
            'preAuthExpiresIn': 300,
            'clinic': {'id': clinicA, 'name': 'Example Clinic'},
            'branches': [
              {'id': branchMain, 'name': 'Main branch'},
              {'id': branchNorth, 'name': 'North branch'},
            ],
          }),
        )
        ..on('POST /auth/login/branch', (_) => json(authenticated()))
        ..on(
          'GET /me',
          (_) =>
              json(staffMe(branchId: branchNorth, branchName: 'North branch')),
        );
      await pumpApp(tester, api: api);

      await login(tester);
      expect(find.text('Elige la clínica'), findsOneWidget);
      expect(find.text('Sample Dental'), findsOneWidget);

      await tapText(tester, 'Example Clinic');
      expect(api.calls('POST /auth/login/clinic').single.body, {
        'preAuthToken': 'pre-auth-1',
        'clinicId': clinicA,
      });
      expect(find.text('Elige la sucursal'), findsOneWidget);

      await tapText(tester, 'North branch');
      expect(api.calls('POST /auth/login/branch').single.body, {
        'preAuthToken': 'pre-auth-2',
        'branchId': branchNorth,
      });
      expect(find.text('Hola, Jane Doe'), findsOneWidget);
      expect(find.text('Elige la sucursal'), findsNothing);
      expect(find.byType(BackButton), findsNothing);
    });

    testWidgets(
      'an expired pre-auth token sends the person back to the login',
      (tester) async {
        final api = FakeApi()
          ..on(
            'POST /auth/login',
            (_) => json({
              'status': 'CLINIC_SELECTION_REQUIRED',
              'preAuthToken': 'pre-auth-1',
              'preAuthExpiresIn': 300,
              'clinics': [
                {'id': clinicA, 'name': 'Example Clinic'},
                {'id': clinicB, 'name': 'Sample Dental'},
              ],
            }),
          )
          ..on(
            'POST /auth/login/clinic',
            (_) => problem('INVALID_SESSION_TOKEN', 401),
          );
        await pumpApp(tester, api: api);

        await login(tester);
        await tapText(tester, 'Sample Dental');

        expect(
          find.textContaining('El inicio de sesión expiró'),
          findsOneWidget,
        );
        await tapText(tester, 'Volver a iniciar sesión');
        expect(find.text('Ingresar'), findsOneWidget);
      },
    );
  });

  group('Session', () {
    testWidgets('restores the stored session and switches branch', (
      tester,
    ) async {
      var branch = branchMain;
      final api = FakeApi()
        ..on(
          'GET /me',
          (_) => json(
            staffMe(
              branchId: branch,
              branchName: branch == branchMain ? 'Main branch' : 'North branch',
            ),
          ),
        )
        ..on('POST /auth/switch-branch', (_) {
          branch = branchNorth;
          return json(authenticated(access: 'north', refresh: 'north-r'));
        });
      final app = await pumpApp(tester, api: api, stored: storedTokens());

      expect(find.text('Main branch'), findsOneWidget);
      await tapText(tester, 'Cambiar');
      await tapText(tester, 'North branch');

      expect(api.calls('POST /auth/switch-branch').single.body, {
        'branchId': branchNorth,
        'refreshToken': 'stored-refresh',
      });
      expect(find.text('Ahora trabajas en North branch.'), findsOneWidget);
      expect(app.store.tokens!.refreshToken, 'north-r');
    });

    testWidgets('logout revokes the refresh token and shows the login', (
      tester,
    ) async {
      final api = FakeApi()
        ..on('GET /me', (_) => json(staffMe()))
        ..on('POST /auth/logout', (_) => json(null, 204));
      final app = await pumpApp(tester, api: api, stored: storedTokens());

      await tester.tap(find.byTooltip('Cerrar sesión'));
      await tester.pumpAndSettle();

      expect(find.text('Ingresar'), findsOneWidget);
      expect(api.calls('POST /auth/logout').single.body, {
        'refreshToken': 'stored-refresh',
      });
      expect(app.store.tokens, isNull);
    });

    testWidgets('an expired session returns to the login with a notice', (
      tester,
    ) async {
      final api = FakeApi()
        ..on(
          'POST /auth/refresh',
          (_) => problem('INVALID_SESSION_TOKEN', 401),
        );
      await pumpApp(
        tester,
        api: api,
        stored: storedTokens(accessLeft: Duration.zero),
      );

      expect(
        find.text('Tu sesión terminó. Inicia sesión de nuevo.'),
        findsOneWidget,
      );
    });

    testWidgets('a patient sees only the patient menu and no branch', (
      tester,
    ) async {
      final api = FakeApi()..on('GET /me', (_) => json(patientMe()));
      await pumpApp(tester, api: api, stored: storedTokens());

      expect(find.text('Hola, John Roe'), findsOneWidget);
      expect(find.text('Mis contactos autorizados'), findsOneWidget);
      expect(find.text('Sucursal activa'), findsNothing);
      expect(find.text('Pacientes'), findsNothing);
    });
  });

  group('Offline', () {
    testWidgets('blocks sending and resumes when the connection returns', (
      tester,
    ) async {
      final api = FakeApi()
        ..on('POST /auth/login', (_) => json(authenticated()))
        ..on('GET /me', (_) => json(staffMe()));
      final app = await pumpApp(tester, api: api, online: false);

      expect(find.textContaining('Sin conexión.'), findsOneWidget);
      await enterText(tester, 'Correo', 'jane.doe@example.com');
      await enterText(tester, 'Contraseña', 'a-long-example-passphrase');
      final submit = tester.widget<FilledButton>(button('Ingresar'));
      expect(submit.onPressed, isNull);
      await tester.tap(find.text('Ingresar'));
      await tester.pumpAndSettle();
      expect(api.calls('POST /auth/login'), isEmpty);

      app.connectivity.online = true;
      await tester.pumpAndSettle();
      expect(find.textContaining('Sin conexión.'), findsNothing);
      await tapText(tester, 'Ingresar');
      expect(api.calls('POST /auth/login'), hasLength(1));
    });

    testWidgets('a stored session that cannot load /me offers a retry', (
      tester,
    ) async {
      var fail = true;
      final api = FakeApi()
        ..on(
          'GET /me',
          (_) => fail ? problem('INTERNAL_ERROR', 503) : json(staffMe()),
        );
      final app = await pumpApp(tester, api: api, stored: storedTokens());

      expect(
        find.textContaining('No pudimos cargar tu sesión'),
        findsOneWidget,
      );
      fail = false;
      await tapText(tester, 'Reintentar');

      expect(app.services.session.status, SessionStatus.active);
      expect(find.text('Hola, Jane Doe'), findsOneWidget);
    });
  });

  group('Deep links', () {
    Uri link(String path) =>
        Uri.parse('https://$linkHost$path#t=$oneTimeToken');

    testWidgets(
      'an invitation shows the clinic, sets the password and logs in',
      (tester) async {
        final api = FakeApi()
          ..on(
            'POST /auth/invitations/preview',
            (_) => json({
              'clinicName': 'Example Clinic',
              'maskedEmail': 'j***@example.com',
              'kind': 'STAFF',
              'expiresAt': '2030-01-04T15:00:00Z',
            }),
          )
          ..on('POST /auth/invitations/accept', (_) => json(authenticated()))
          ..on('GET /me', (_) => json(staffMe()));
        final app = await pumpApp(tester, api: api);

        app.links.open(link('/invitation'));
        await tester.pumpAndSettle();

        expect(api.calls('POST /auth/invitations/preview').single.body, {
          'token': oneTimeToken,
        });
        expect(find.text('Te invitaron a Example Clinic'), findsOneWidget);
        expect(find.text('Cuenta: j***@example.com'), findsOneWidget);

        await enterText(tester, 'Contraseña', 'short');
        await tapText(tester, 'Activar cuenta');
        expect(find.text('Usa al menos 12 caracteres.'), findsOneWidget);
        expect(api.calls('POST /auth/invitations/accept'), isEmpty);

        await enterText(tester, 'Contraseña', 'a-long-example-passphrase');
        await enterText(
          tester,
          'Repite la contraseña',
          'a-long-example-passphrase',
        );
        await tapText(tester, 'Activar cuenta');

        expect(api.calls('POST /auth/invitations/accept').single.body, {
          'token': oneTimeToken,
          'password': 'a-long-example-passphrase',
        });
        expect(find.text('Hola, Jane Doe'), findsOneWidget);
      },
    );

    testWidgets('an expired or used invitation is rejected in Spanish', (
      tester,
    ) async {
      final api = FakeApi()
        ..on(
          'POST /auth/invitations/preview',
          (_) => problem('ONE_TIME_TOKEN_INVALID', 400),
        );
      final app = await pumpApp(tester, api: api);

      app.links.open(link('/invitation'));
      await tester.pumpAndSettle();

      expect(
        find.text(
          'La invitación expiró o ya se usó. Pide a la clínica que te la reenvíe.',
        ),
        findsOneWidget,
      );
    });

    testWidgets(
      'a link that launches the app opens after the session is restored',
      (tester) async {
        final api = FakeApi()
          ..on('GET /me', (_) => json(staffMe()))
          ..on(
            'POST /auth/invitations/preview',
            (_) => problem('ONE_TIME_TOKEN_INVALID', 400),
          );
        await pumpApp(
          tester,
          api: api,
          stored: storedTokens(),
          beforeRestore: (links) => links.open(link('/invitation')),
        );

        expect(api.calls('GET /me'), hasLength(1));
        expect(find.textContaining('La invitación expiró'), findsOneWidget);
        await tapText(tester, 'Ir a iniciar sesión');
        expect(find.text('Hola, Jane Doe'), findsOneWidget);
      },
    );

    testWidgets('a reset link sets the new password once', (tester) async {
      final api = FakeApi()
        ..on('POST /auth/password-reset/confirm', (_) => json(null, 204));
      final app = await pumpApp(tester, api: api);
      app.links.open(link('/password-reset'));
      await tester.pumpAndSettle();

      expect(find.text('Nueva contraseña'), findsWidgets);
      await enterText(tester, 'Nueva contraseña', 'a-brand-new-passphrase');
      await enterText(tester, 'Repite la contraseña', 'a-brand-new-passphrase');
      await tapText(tester, 'Guardar contraseña');

      expect(api.calls('POST /auth/password-reset/confirm').single.body, {
        'token': oneTimeToken,
        'newPassword': 'a-brand-new-passphrase',
      });
      expect(find.textContaining('Tu contraseña se actualizó'), findsOneWidget);
      await tapText(tester, 'Ir a iniciar sesión');
      expect(find.text('Ingresar'), findsOneWidget);
    });

    testWidgets('an expired reset link is rejected', (tester) async {
      final api = FakeApi()
        ..on(
          'POST /auth/password-reset/confirm',
          (_) => problem('ONE_TIME_TOKEN_INVALID', 400),
        );
      final app = await pumpApp(tester, api: api);

      app.links.open(link('/password-reset'));
      await tester.pumpAndSettle();
      await enterText(tester, 'Nueva contraseña', 'a-brand-new-passphrase');
      await enterText(tester, 'Repite la contraseña', 'a-brand-new-passphrase');
      await tapText(tester, 'Guardar contraseña');

      expect(
        find.text('El enlace expiró o ya se usó. Solicita uno nuevo.'),
        findsOneWidget,
      );
    });

    testWidgets('email verification confirms the token', (tester) async {
      final api = FakeApi()
        ..on('POST /auth/email-verification/confirm', (_) => json(null, 204));
      final app = await pumpApp(tester, api: api);

      app.links.open(link('/email-verification'));
      await tester.pumpAndSettle();
      await tapText(tester, 'Confirmar mi correo');

      expect(api.calls('POST /auth/email-verification/confirm').single.body, {
        'token': oneTimeToken,
      });
      expect(
        find.text('Tu correo quedó verificado. Ya puedes iniciar sesión.'),
        findsOneWidget,
      );
    });

    testWidgets('links of another host are ignored', (tester) async {
      final app = await pumpApp(tester);

      app.links.open(
        Uri.parse('https://evil.example/invitation#t=$oneTimeToken'),
      );
      await tester.pumpAndSettle();

      expect(find.text('Ingresar'), findsOneWidget);
      expect(app.api.requests, isEmpty);
    });
  });

  group('Password reset request', () {
    testWidgets('always shows the same confirmation', (tester) async {
      final api = FakeApi()
        ..on('POST /auth/password-reset/request', (_) => json(null, 202));
      await pumpApp(tester, api: api);

      await tapText(tester, '¿Olvidaste tu contraseña?');
      await enterText(tester, 'Correo', 'unknown@example.com');
      await tapText(tester, 'Enviar enlace');

      expect(api.calls('POST /auth/password-reset/request').single.body, {
        'email': 'unknown@example.com',
      });
      expect(
        find.textContaining('Si el correo está registrado'),
        findsOneWidget,
      );
    });
  });

  testWidgets('login screen meets accessibility guidelines', (tester) async {
    final handle = tester.ensureSemantics();
    await pumpApp(tester);

    await expectLater(tester, meetsGuideline(androidTapTargetGuideline));
    await expectLater(tester, meetsGuideline(iOSTapTargetGuideline));
    await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
    await expectLater(tester, meetsGuideline(textContrastGuideline));
    handle.dispose();
  });

  testWidgets('home screen meets accessibility guidelines', (tester) async {
    final handle = tester.ensureSemantics();
    final api = FakeApi()..on('GET /me', (_) => json(staffMe()));
    await pumpApp(tester, api: api, stored: storedTokens());

    await expectLater(tester, meetsGuideline(androidTapTargetGuideline));
    await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
    await expectLater(tester, meetsGuideline(textContrastGuideline));
    handle.dispose();
  });
}
