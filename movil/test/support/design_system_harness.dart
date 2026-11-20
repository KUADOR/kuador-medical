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
/// next to the calling test file (`flutter test --update-goldens` to accept).
/// Goldens only use synthetic data.
void testGoldens(
  String name,
  Widget Function() builder, {
  Size size = const Size(360, 640),
}) {
  for (final mode in const [ThemeMode.light, ThemeMode.dark]) {
    testWidgets('$name matches its ${mode.name} golden', (tester) async {
      await tester.binding.setSurfaceSize(size);
      addTearDown(() => tester.binding.setSurfaceSize(null));
      await tester.pumpWidget(themedApp(builder(), mode: mode));
      await tester.pumpAndSettle();
      await expectLater(
        find.byType(MaterialApp),
        matchesGoldenFile('goldens/${name}_${mode.name}.png'),
      );
    });
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
void testA11yGuidelines(String name, Widget Function() builder) {
  for (final mode in const [ThemeMode.light, ThemeMode.dark]) {
    testWidgets('$name meets the a11y guidelines in ${mode.name}', (
      tester,
    ) async {
      await tester.pumpWidget(themedApp(builder(), mode: mode));
      await tester.pumpAndSettle();
      await expectMeetsA11yGuidelines(tester);
    });
  }
}
