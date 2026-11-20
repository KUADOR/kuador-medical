import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:medical_kuador/config.dart';
import 'package:medical_kuador/core/api/api_client.dart';
import 'package:medical_kuador/core/api/api_exception.dart';
import 'package:medical_kuador/core/menu/app_menu.dart';
import 'package:medical_kuador/core/session/session_models.dart';
import 'package:medical_kuador/core/util/client_id.dart';
import 'package:medical_kuador/features/auth/data/registration_catalog.dart';
import 'package:medical_kuador/features/auth/deep_links/deep_link.dart';
import 'package:medical_kuador/features/auth/ui/auth_messages.dart';

import '../support/fake_api.dart';

void main() {
  group('parseDeepLink', () {
    const token = 'abcdefghijklmnopqrstuvwxyz012345';

    DeepLink? parse(String url, {String host = linkHost}) =>
        parseDeepLink(Uri.parse(url), allowedHost: host);

    test('reads the token from the fragment of each route', () {
      expect(
        parse('https://$linkHost/invitation#t=$token'),
        isA<InvitationLink>().having((l) => l.token, 'token', token),
      );
      expect(
        parse('https://$linkHost/password-reset#t=$token'),
        isA<PasswordResetLink>(),
      );
      expect(
        parse('https://$linkHost/email-verification/#t=$token'),
        isA<EmailVerificationLink>(),
      );
    });

    test('decodes a percent-encoded token', () {
      expect(
        parse('https://$linkHost/invitation#t=abc%2Bdefghijklmnopq')!.token,
        'abc+defghijklmnopq',
      );
    });

    test('ignores a token in the query string (only the fragment counts)', () {
      expect(parse('https://$linkHost/invitation?t=$token'), isNull);
    });

    test('rejects other hosts, schemes, routes and malformed tokens', () {
      expect(parse('https://evil.example/invitation#t=$token'), isNull);
      expect(parse('http://$linkHost/invitation#t=$token'), isNull);
      expect(parse('medicalkuador://invitation#t=$token'), isNull);
      expect(parse('https://$linkHost/other#t=$token'), isNull);
      expect(parse('https://$linkHost/invitation#t=short'), isNull);
      expect(parse('https://$linkHost/invitation'), isNull);
    });

    test('accepts any https host when no deep link host is configured', () {
      expect(
        parse('https://web.local/invitation#t=$token', host: ''),
        isNotNull,
      );
    });
  });

  group('buildMenu', () {
    CurrentSession me(Map<String, Object?> json) =>
        CurrentSession.fromJson(json);

    test('reception sees only patients', () {
      final labels = buildMenu(me(staffMe())).map((e) => e.label);
      expect(labels, ['Pacientes']);
    });

    test(
      'an administrator sees staff, roles, patients and self-registration',
      () {
        final labels = buildMenu(
          me(
            staffMe(
              permissions: [
                'identity.users.read',
                'roles.read',
                'patients.read',
                'patients.selfRegistration.configure',
              ],
            ),
          ),
        ).map((e) => e.label);
        expect(labels, [
          'Personal',
          'Roles y permisos',
          'Pacientes',
          'Autorregistro de pacientes',
        ]);
      },
    );

    test('a module not enabled by the plan hides its entries', () {
      final labels = buildMenu(
        me(
          staffMe(
            permissions: ['identity.users.read', 'patients.read'],
            modules: ['identity'],
          ),
        ),
      ).map((e) => e.label);
      expect(labels, ['Personal']);
    });

    test('a patient sees only the patient entries', () {
      final labels = buildMenu(me(patientMe())).map((e) => e.label);
      expect(labels, ['Mis contactos autorizados']);
    });

    test('nothing is shown while the password must be changed', () {
      expect(
        buildMenu(
          me(staffMe(permissions: ['patients.read'], mustChangePassword: true)),
        ),
        isEmpty,
      );
    });
  });

  group('ApiClient', () {
    test(
      'maps Problem Details to ApiException with field errors and Retry-After',
      () async {
        final api = FakeApi()
          ..on(
            'POST /auth/login',
            (_) => problem(
              'TOO_MANY_ATTEMPTS',
              429,
              headers: {'retry-after': '120'},
            ),
          )
          ..on(
            'POST /x',
            (_) => problem(
              'VALIDATION',
              400,
              errors: [
                {'field': 'birthDate', 'code': 'REQUIRED'},
              ],
            ),
          );
        final client = ApiClient(httpClient: api.client, baseUrl: '$baseUrl/');

        await expectLater(
          client.post('/auth/login', body: {}),
          throwsA(
            isA<ApiException>()
                .having((e) => e.code, 'code', 'TOO_MANY_ATTEMPTS')
                .having(
                  (e) => e.retryAfter,
                  'retryAfter',
                  const Duration(minutes: 2),
                ),
          ),
        );
        await expectLater(
          client.post('/x', body: {}),
          throwsA(
            isA<ApiException>().having(
              (e) => e.fieldErrors.single.field,
              'field',
              'birthDate',
            ),
          ),
        );
        expect(
          api.requests.first.request.url.toString(),
          '$baseUrl/auth/login',
        );
      },
    );

    test('reports transport failures as UNREACHABLE', () async {
      final api = FakeApi()
        ..on(
          'GET /me',
          (r) => throw http.ClientException('offline', r.request.url),
        );
      final client = ApiClient(httpClient: api.client, baseUrl: baseUrl);

      await expectLater(
        client.get('/me', authenticated: false),
        throwsA(
          isA<ApiException>().having(
            (e) => e.isUnreachable,
            'unreachable',
            true,
          ),
        ),
      );
    });

    test('ApiException.toString never includes bodies', () {
      expect(
        const ApiException(code: 'VALIDATION', status: 400).toString(),
        'ApiException(VALIDATION, status: 400)',
      );
    });
  });

  group('auth messages', () {
    test('invalid credentials are generic', () {
      expect(
        authErrorMessage(
          const ApiException(code: 'INVALID_CREDENTIALS', status: 401),
        ),
        'El correo o la contraseña no son correctos.',
      );
    });

    test('the lockout tells when to retry', () {
      expect(
        authErrorMessage(
          const ApiException(
            code: 'TOO_MANY_ATTEMPTS',
            status: 429,
            retryAfter: Duration(seconds: 900),
          ),
        ),
        contains('15 minutos'),
      );
    });
  });

  test('ageOn counts whole years', () {
    expect(ageOn(DateTime(2012, 1, 2), DateTime(2030, 1, 1)), 17);
    expect(ageOn(DateTime(2012, 1, 1), DateTime(2030, 1, 1)), 18);
  });

  test('newClientId returns a UUID v4', () {
    expect(
      newClientId(),
      matches(
        RegExp(
          r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$',
        ),
      ),
    );
  });

  test('AppConfig defaults', () {
    final config = AppConfig.fromEnvironment();
    expect(config.apiBaseUrl, 'http://10.0.2.2:8080/api/v1');
    expect(config.appLinkHost, '');
    expect(config.consentTextVersion, AppConfig.defaultConsentTextVersion);
  });
}
