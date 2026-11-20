// Mobile app against the local api (MEDK-31, plan MEDK-26 decisions 6 and 7).
//
// The responses are the examples of `contracts/local-integration.openapi.yaml`,
// whose paths are absolute from the api origin (`/api/v1/...`).
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:medical_kuador/app.dart';
import 'package:medical_kuador/config.dart';
import 'package:medical_kuador/core/config/api_config.dart';
import 'package:medical_kuador/core/connectivity/connectivity_monitor.dart';
import 'package:medical_kuador/core/session/token_store.dart';
import 'package:medical_kuador/features/auth/deep_links/deep_link.dart';
import 'package:medical_kuador/main.dart';

const email = 'jane.doe@example.com';
const password = 'a-long-example-passphrase';
const accessToken = 'eyJhbGciOiJIUzI1NiJ9.example-payload.example-signature';
const refreshToken = 'refresh-example-token';

/// `authenticated` example of `POST /api/v1/auth/login`.
const loginExample = {
  'status': 'AUTHENTICATED',
  'accessToken': accessToken,
  'tokenType': 'Bearer',
  'expiresIn': 900,
  'refreshToken': refreshToken,
  'refreshExpiresIn': 604800,
  'scope': 'TENANT',
  'clinic': {
    'id': '5b0c8f2e-3a51-4d6e-9f0a-1c2b3d4e5f60',
    'name': 'Example Clinic A',
  },
  'branch': {
    'id': '2f6d1c0a-8b3e-4a7d-9c5f-6e1b2a3c4d5e',
    'name': 'Main branch',
  },
  'mustChangePassword': false,
};

/// `staff` example of `GET /api/v1/me`.
const meExample = {
  'accountId': '0f1e2d3c-4b5a-4968-8776-655443322110',
  'kind': 'STAFF',
  'scope': 'TENANT',
  'displayName': 'Jane Doe',
  'clinic': {
    'id': '5b0c8f2e-3a51-4d6e-9f0a-1c2b3d4e5f60',
    'name': 'Example Clinic A',
  },
  'activeBranch': {
    'id': '2f6d1c0a-8b3e-4a7d-9c5f-6e1b2a3c4d5e',
    'name': 'Main branch',
  },
  'availableBranches': [
    {'id': '2f6d1c0a-8b3e-4a7d-9c5f-6e1b2a3c4d5e', 'name': 'Main branch'},
  ],
  'roles': ['OWNER'],
  'permissions': ['roles.read', 'identity.users.read', 'patients.read'],
  'enabledModules': ['identity', 'patients'],
  'mustChangePassword': false,
};

const unreachableMessage =
    'No pudimos conectarnos con el servidor. Revisa tu conexión e inténtalo de nuevo.';

http.Response jsonResponse(Object body, [int status = 200]) => http.Response(
  jsonEncode(body),
  status,
  headers: {'content-type': 'application/json'},
);

Future<void> pumpLocalApp(WidgetTester tester, http.Client client) async {
  final config = AppConfig(
    apiBaseUrl: ApiConfig.parse(ApiConfig.defaultApiBaseUrl).apiV1Url,
  );
  await tester.pumpWidget(
    MedicalKuadorApp(
      services: buildServices(
        config: config,
        httpClient: client,
        tokenStore: MemoryTokenStore(),
        clock: () => DateTime.utc(2030, 1, 1, 12),
      ),
      connectivity: FakeConnectivityMonitor(online: true),
      deepLinks: FakeDeepLinkSource(),
    ),
  );
  await tester.pumpAndSettle();
}

Future<void> submitLogin(WidgetTester tester) async {
  await tester.enterText(find.widgetWithText(TextFormField, 'Correo'), email);
  await tester.enterText(
    find.widgetWithText(TextFormField, 'Contraseña'),
    password,
  );
  await tester.tap(find.text('Ingresar').last);
  await tester.pumpAndSettle();
}

void main() {
  final logged = <String>[];

  /// Runs [body] capturing `debugPrint`, restoring it before the test ends
  /// (the test binding checks that foundation debug variables are reset).
  Future<void> capturingLogs(Future<void> Function() body) async {
    logged.clear();
    final original = debugPrint;
    debugPrint = (String? message, {int? wrapWidth}) {
      if (message != null) logged.add(message);
    };
    try {
      await body();
    } finally {
      debugPrint = original;
    }
  }

  void expectNothingSensitiveLogged() {
    for (final line in logged) {
      for (final secret in [accessToken, refreshToken, password, email]) {
        expect(line, isNot(contains(secret)));
      }
    }
  }

  testWidgets(
    'logs in against the default local api and shows the clinic from /me',
    (tester) => capturingLogs(() async {
      final requests = <http.Request>[];
      final client = MockClient((request) async {
        requests.add(request);
        return switch ('${request.method} ${request.url.path}') {
          'POST /api/v1/auth/login' => jsonResponse(loginExample),
          'GET /api/v1/me' => jsonResponse(meExample),
          _ => jsonResponse({'code': 'NOT_FOUND', 'status': 404}, 404),
        };
      });
      await pumpLocalApp(tester, client);

      await submitLogin(tester);

      final login = requests.firstWhere((r) => r.url.path.endsWith('/login'));
      expect(login.url.toString(), 'http://10.0.2.2:8080/api/v1/auth/login');
      expect(jsonDecode(login.body), {'email': email, 'password': password});

      final me = requests.lastWhere((r) => r.url.path == '/api/v1/me');
      expect(me.url.toString(), 'http://10.0.2.2:8080/api/v1/me');
      expect(me.headers['Authorization'], 'Bearer $accessToken');
      // The tenant comes only from the token: no tenant hint is sent.
      expect(me.url.query, isEmpty);
      expect(
        me.headers.keys.map((k) => k.toLowerCase()),
        isNot(anyElement(contains('tenant'))),
      );

      expect(find.text('Example Clinic A'), findsOneWidget);
      expect(find.text('Hola, Jane Doe'), findsOneWidget);
      expectNothingSensitiveLogged();
    }),
  );

  testWidgets(
    'says in Spanish that the server could not be reached',
    (tester) => capturingLogs(() async {
      final client = MockClient(
        (request) async =>
            throw http.ClientException('Connection refused', request.url),
      );
      await pumpLocalApp(tester, client);

      await submitLogin(tester);

      expect(find.text(unreachableMessage), findsOneWidget);
      expect(find.text('Iniciar sesión'), findsWidgets);
      expectNothingSensitiveLogged();
    }),
  );

  test('the app code never prints or logs', () {
    final offenders = <String>[];
    final pattern = RegExp(r'\b(print|debugPrint|log|debugPrintStack)\s*\(');
    for (final file in Directory('lib').listSync(recursive: true)) {
      if (file is! File || !file.path.endsWith('.dart')) continue;
      final lines = file.readAsLinesSync();
      for (var i = 0; i < lines.length; i++) {
        final code = lines[i].split('//').first;
        if (pattern.hasMatch(code)) offenders.add('${file.path}:${i + 1}');
      }
    }
    expect(offenders, isEmpty);
  });
}
