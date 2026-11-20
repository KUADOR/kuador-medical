import 'dart:io' show Platform;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:medical_kuador/design_system/theme.dart';

/// Wraps [child] in a MaterialApp with the app theme forced to [mode].
Widget themedApp(Widget child, {ThemeMode mode = ThemeMode.light}) {
  return MaterialApp(
    debugShowCheckedModeBanner: false,
    theme: KdTheme.light,
    darkTheme: KdTheme.dark,
    themeMode: mode,
    builder: KdTheme.builder,
    home: child,
  );
}

/// Simulates the platform "reduce motion" setting for the current test.
void simulateReducedMotion(WidgetTester tester) {
  tester.platformDispatcher.accessibilityFeaturesTestValue =
      const FakeAccessibilityFeatures(disableAnimations: true);
  addTearDown(tester.platformDispatcher.clearAccessibilityFeaturesTestValue);
}

/// Golden test of [builder] in the light and the dark theme.
///
/// Writes/compares `goldens/<name>_light.png` and `goldens/<name>_dark.png`
/// next to the calling test file. Goldens only use synthetic data.
///
/// Tagged `golden` and compared exactly. Baselines are the Linux renderings of
/// CI (Flutter 3.47.9), so the comparison is skipped on macOS and Windows,
/// whose text and antialiasing differ slightly (README § Design system).
///
/// With [reduceMotion] the platform asks to disable animations, for widgets
/// that would otherwise never settle (a spinner).
void testGoldens(
  String name,
  Widget Function() builder, {
  Size size = const Size(360, 640),
  bool reduceMotion = false,
}) {
  for (final mode in const [ThemeMode.light, ThemeMode.dark]) {
    testWidgets(
      '$name matches its ${mode.name} golden',
      tags: 'golden',
      skip: !Platform.isLinux,
      (tester) async {
        if (reduceMotion) simulateReducedMotion(tester);
        await tester.binding.setSurfaceSize(size);
        addTearDown(() => tester.binding.setSurfaceSize(null));
        await tester.pumpWidget(themedApp(builder(), mode: mode));
        await settleWithAssets(tester);
        await expectLater(
          find.byType(MaterialApp),
          matchesGoldenFile('goldens/${name}_${mode.name}.png'),
        );
      },
    );
  }
}

/// Checks the accessibility guidelines the design system promises (AA):
/// Android (48 dp) and iOS (44 pt) tap targets, labeled tap targets and text
/// contrast. Call after pumping the widget under test.
Future<void> expectMeetsA11yGuidelines(WidgetTester tester) async {
  final handle = tester.ensureSemantics();
  try {
    await expectLater(tester, meetsGuideline(androidTapTargetGuideline));
    await expectLater(tester, meetsGuideline(iOSTapTargetGuideline));
    await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
    await expectLater(tester, meetsGuideline(textContrastGuideline));
  } finally {
    handle.dispose();
  }
}

/// Runs [expectMeetsA11yGuidelines] on [builder] in the light and dark theme.
///
/// With [reduceMotion] the platform asks to disable animations.
void testA11yGuidelines(
  String name,
  Widget Function() builder, {
  bool reduceMotion = false,
}) {
  for (final mode in const [ThemeMode.light, ThemeMode.dark]) {
    testWidgets('$name meets the a11y guidelines in ${mode.name}', (
      tester,
    ) async {
      if (reduceMotion) simulateReducedMotion(tester);
      await tester.pumpWidget(themedApp(builder(), mode: mode));
      await settleWithAssets(tester);
      await expectMeetsA11yGuidelines(tester);
    });
  }
}

/// Settles the frame and lets asset loads (the SVG icons) finish, so they
/// are drawn in goldens and accessibility checks.
Future<void> settleWithAssets(WidgetTester tester) async {
  await tester.pumpAndSettle();
  await tester.runAsync(() => Future<void>.delayed(Duration.zero));
  await tester.pumpAndSettle();
}
