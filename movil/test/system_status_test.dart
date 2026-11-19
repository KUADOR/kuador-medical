import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:medical_kuador/config.dart';
import 'package:medical_kuador/main.dart';
import 'package:medical_kuador/system_status/system_info.dart';
import 'package:medical_kuador/system_status/system_info_client.dart';

const baseUrl = 'https://api.example.test/api/v1';

http.Response jsonResponse(Object body, [int status = 200]) => http.Response(
  jsonEncode(body),
  status,
  headers: {'content-type': 'application/json'},
);

const upBody = {
  'name': 'medical-kuador-api',
  'version': '0.1.0',
  'status': 'UP',
  'database': 'UP',
};

Future<void> pumpApp(WidgetTester tester, MockClient httpClient) async {
  final client = SystemInfoClient(httpClient: httpClient, baseUrl: baseUrl);
  await tester.pumpWidget(MedicalKuadorApp(systemInfoClient: client));
}

void main() {
  group('SystemStatusScreen', () {
    testWidgets('shows a loading state, then the system status', (
      tester,
    ) async {
      final requests = <http.Request>[];
      await pumpApp(
        tester,
        MockClient((request) async {
          requests.add(request);
          return jsonResponse(upBody);
        }),
      );

      expect(find.text('Consultando el estado del sistema…'), findsOneWidget);

      await tester.pumpAndSettle();

      expect(requests.single.method, 'GET');
      expect(requests.single.url.toString(), '$baseUrl/system/info');
      expect(find.text('Estado del sistema'), findsOneWidget);
      expect(find.text('El sistema funciona correctamente'), findsOneWidget);
      expect(find.text('Aplicación'), findsOneWidget);
      expect(find.text('Base de datos'), findsOneWidget);
      expect(find.text('Disponible'), findsNWidgets(2));
      expect(find.text('0.1.0'), findsOneWidget);
    });

    testWidgets('reports a database that is down', (tester) async {
      await pumpApp(
        tester,
        MockClient((_) async {
          return jsonResponse({...upBody, 'database': 'DOWN'});
        }),
      );
      await tester.pumpAndSettle();

      expect(find.text('El sistema tiene fallas'), findsOneWidget);
      expect(find.text('No disponible'), findsOneWidget);
      expect(find.text('Disponible'), findsOneWidget);
    });

    testWidgets('shows a connection error and retries', (tester) async {
      var calls = 0;
      await pumpApp(
        tester,
        MockClient((request) async {
          calls++;
          if (calls == 1) throw http.ClientException('offline', request.url);
          return jsonResponse(upBody);
        }),
      );
      await tester.pumpAndSettle();

      expect(
        find.text(
          'No pudimos conectarnos con el servidor. '
          'Revisa tu conexión e inténtalo de nuevo.',
        ),
        findsOneWidget,
      );

      await tester.tap(find.text('Reintentar'));
      await tester.pumpAndSettle();

      expect(calls, 2);
      expect(find.text('El sistema funciona correctamente'), findsOneWidget);
    });

    testWidgets('shows a server error for a problem response', (tester) async {
      await pumpApp(
        tester,
        MockClient((_) async {
          return jsonResponse({
            'type': 'about:blank',
            'title': 'Internal Server Error',
            'status': 500,
            'code': 'INTERNAL_ERROR',
          }, 500);
        }),
      );
      await tester.pumpAndSettle();

      expect(
        find.text(
          'El servidor no pudo informar su estado. '
          'Inténtalo de nuevo en unos minutos.',
        ),
        findsOneWidget,
      );
      expect(find.widgetWithText(FilledButton, 'Reintentar'), findsOneWidget);
    });

    testWidgets('shows an unexpected-response error for an invalid body', (
      tester,
    ) async {
      await pumpApp(
        tester,
        MockClient((_) async {
          return jsonResponse({'name': 'x', 'version': '1', 'status': 'MAYBE'});
        }),
      );
      await tester.pumpAndSettle();

      expect(
        find.text(
          'Recibimos una respuesta inesperada del servidor. '
          'Inténtalo de nuevo.',
        ),
        findsOneWidget,
      );
    });

    testWidgets('meets accessibility guidelines', (tester) async {
      final handle = tester.ensureSemantics();
      await pumpApp(tester, MockClient((_) async => jsonResponse(upBody)));
      await tester.pumpAndSettle();

      await expectLater(tester, meetsGuideline(androidTapTargetGuideline));
      await expectLater(tester, meetsGuideline(iOSTapTargetGuideline));
      await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
      await expectLater(tester, meetsGuideline(textContrastGuideline));
      handle.dispose();
    });
  });

  group('SystemInfoClient', () {
    test('tolerates a trailing slash in the base URL', () async {
      Uri? called;
      final client = SystemInfoClient(
        httpClient: MockClient((request) async {
          called = request.url;
          return jsonResponse(upBody);
        }),
        baseUrl: '$baseUrl/',
      );

      final info = await client.fetch();

      expect(called.toString(), '$baseUrl/system/info');
      expect(info.status, HealthStatus.up);
      expect(info.name, 'medical-kuador-api');
    });
  });

  group('AppConfig', () {
    test('falls back to the local API when API_BASE_URL is not defined', () {
      expect(
        AppConfig.fromEnvironment().apiBaseUrl,
        AppConfig.defaultApiBaseUrl,
      );
    });
  });
}
