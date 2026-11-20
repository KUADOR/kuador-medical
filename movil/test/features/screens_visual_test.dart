import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:medical_kuador/app_scope.dart';
import 'package:medical_kuador/config.dart';
import 'package:medical_kuador/core/connectivity/connectivity_monitor.dart';
import 'package:medical_kuador/core/connectivity/connectivity_widgets.dart';
import 'package:medical_kuador/core/session/session_models.dart';
import 'package:medical_kuador/core/session/token_store.dart';
import 'package:medical_kuador/features/home/home_screen.dart';
import 'package:medical_kuador/features/patients/ui/my_guardian_links_screen.dart';
import 'package:medical_kuador/main.dart';
import 'package:medical_kuador/system_status/system_info_client.dart';
import 'package:medical_kuador/system_status/system_status_screen.dart';

import '../support/design_system_harness.dart';
import '../support/fake_api.dart';
import '../support/module_fixtures.dart';

/// Visual migration of the remaining mobile screens (MEDK-47): goldens in
/// light and dark and the accessibility guidelines, with synthetic data only.

/// [child] with the app services backed by [api] and a connectivity monitor,
/// shown once the stored (synthetic) session is restored.
Widget _scoped(Widget child, {required FakeApi api, bool online = true}) {
  api.routes.putIfAbsent(
    'GET /me',
    () =>
        (_) => json(patientMe()),
  );
  final services = buildServices(
    config: const AppConfig(apiBaseUrl: baseUrl, appLinkHost: linkHost),
    httpClient: api.client,
    tokenStore: MemoryTokenStore(storedTokens()),
    clock: () => now,
  );
  final restored = services.session.restore();
  return AppScope(
    services: services,
    child: ConnectivityScope(
      monitor: FakeConnectivityMonitor(online: online),
      child: FutureBuilder<void>(
        future: restored,
        builder: (context, snapshot) =>
            snapshot.connectionState == ConnectionState.done
            ? child
            : const SizedBox.shrink(),
      ),
    ),
  );
}

CurrentSession _patient() => CurrentSession.fromJson(patientMe());

/// Staff with two branches: shows the active branch and «Cambiar».
CurrentSession _staffWithBranches() => CurrentSession.fromJson(staffMe());

FakeApi _linksApi() => FakeApi()
  ..on(
    'GET /me/guardian-links',
    (_) => json([
      guardianLink(current: false, endReason: 'ADULTHOOD'),
      guardianLink(
        id: '7a6b5c4d-3e2f-4a1b-8c9d-0e1f2a3b4c5d',
        linkType: 'EMERGENCY_CONTACT',
      ),
    ]),
  );

SystemInfoClient _statusClient({required bool up}) {
  final api = FakeApi()
    ..on(
      'GET /system/info',
      (_) => up
          ? json({
              'name': 'medical-kuador-api',
              'version': '0.1.0',
              'status': 'UP',
              'database': 'UP',
            })
          : problem('INTERNAL', 500),
    );
  return SystemInfoClient(httpClient: api.client, baseUrl: baseUrl);
}

void main() {
  final screens = <String, (Widget Function(), Size, String)>{
    'home_patient': (
      () => _scoped(HomeScreen(session: _patient()), api: FakeApi()),
      const Size(360, 640),
      'Mis contactos autorizados',
    ),
    'home_staff_offline': (
      () => _scoped(
        HomeScreen(session: _staffWithBranches()),
        api: FakeApi(),
        online: false,
      ),
      const Size(360, 800),
      offlineMessage,
    ),
    'my_guardian_links': (
      () => _scoped(const MyGuardianLinksScreen(), api: _linksApi()),
      const Size(360, 1400),
      'Mary Roe',
    ),
    'system_status': (
      () => SystemStatusScreen(client: _statusClient(up: true)),
      const Size(360, 640),
      'El sistema funciona correctamente',
    ),
    'system_status_error': (
      () => SystemStatusScreen(client: _statusClient(up: false)),
      const Size(360, 640),
      'Reintentar',
    ),
  };

  for (final MapEntry(key: name, :value) in screens.entries) {
    final (builder, size, expected) = value;
    group(name, () {
      testGoldens(name, builder, size: size);

      for (final mode in const [ThemeMode.light, ThemeMode.dark]) {
        testWidgets('$name meets the a11y guidelines in ${mode.name}', (
          tester,
        ) async {
          await tester.binding.setSurfaceSize(size);
          addTearDown(() => tester.binding.setSurfaceSize(null));
          await tester.pumpWidget(themedApp(builder(), mode: mode));
          await settleWithAssets(tester);
          expect(find.text(expected), findsWidgets);
          await expectMeetsA11yGuidelines(tester);
        });
      }
    });
  }
}
