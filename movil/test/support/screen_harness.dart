import 'dart:io' show Platform;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:medical_kuador/app_scope.dart';
import 'package:medical_kuador/config.dart';
import 'package:medical_kuador/core/connectivity/connectivity_monitor.dart';
import 'package:medical_kuador/core/connectivity/connectivity_widgets.dart';
import 'package:medical_kuador/core/session/token_store.dart';
import 'package:medical_kuador/main.dart';

import 'design_system_harness.dart';
import 'fake_api.dart';

/// A screen pumped on its own, with the app services (backed by a [FakeApi])
/// and the theme forced to [ThemeMode.light] or [ThemeMode.dark].
///
/// Used for the screen goldens and accessibility checks, where the session
/// flow of `MedicalKuadorApp` is not needed. Fictitious data only (FR-014).
class ScreenCase {
  const ScreenCase(
    this.name,
    this.build, {
    this.api,
    this.prepare,
    this.online = true,
    this.size = const Size(390, 844),
  });

  /// File-friendly name, used in the golden file names.
  final String name;
  final Widget Function() build;

  /// Whether the device has a connection.
  final bool online;

  /// Logical size of the golden (a long form uses a taller one).
  final Size size;

  /// Routes the screen calls while it loads.
  final FakeApi Function()? api;

  /// Interactions after the first frame (fill a field, tap the action).
  final Future<void> Function(WidgetTester tester)? prepare;
}

/// Pumps [screen] with fake services, in [mode].
Future<FakeApi> pumpScreen(
  WidgetTester tester,
  Widget screen, {
  FakeApi? api,
  bool online = true,
  ThemeMode mode = ThemeMode.light,
}) async {
  final fakeApi = api ?? FakeApi();
  final services = buildServices(
    config: const AppConfig(apiBaseUrl: baseUrl, appLinkHost: linkHost),
    httpClient: fakeApi.client,
    tokenStore: MemoryTokenStore(),
    clock: () => now,
  );
  await tester.pumpWidget(
    AppScope(
      services: services,
      child: ConnectivityScope(
        monitor: FakeConnectivityMonitor(online: online),
        child: themedApp(screen, mode: mode),
      ),
    ),
  );
  await settleWithAssets(tester);
  return fakeApi;
}

/// Pumps the [ScreenCase] and runs its preparation.
Future<void> pumpCase(
  WidgetTester tester,
  ScreenCase c, {
  ThemeMode mode = ThemeMode.light,
}) async {
  await pumpScreen(
    tester,
    c.build(),
    api: c.api?.call(),
    online: c.online,
    mode: mode,
  );
  await c.prepare?.call(tester);
  await settleWithAssets(tester);
}

/// Light and dark goldens of [c] at a phone size, next to the test file in
/// `goldens/<prefix>_<name>_<mode>.png`. Linux only, like every golden
/// (README § Goldens).
void testScreenGoldens(String prefix, ScreenCase c) {
  for (final mode in const [ThemeMode.light, ThemeMode.dark]) {
    testWidgets(
      '${c.name} matches its ${mode.name} golden',
      tags: 'golden',
      skip: !Platform.isLinux,
      (tester) async {
        await tester.binding.setSurfaceSize(c.size);
        addTearDown(() => tester.binding.setSurfaceSize(null));
        await pumpCase(tester, c, mode: mode);
        await expectLater(
          find.byType(MaterialApp),
          matchesGoldenFile('goldens/${prefix}_${c.name}_${mode.name}.png'),
        );
      },
    );
  }
}
