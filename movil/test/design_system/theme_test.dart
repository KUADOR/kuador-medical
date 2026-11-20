import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:medical_kuador/design_system/theme.dart';
import 'package:medical_kuador/design_system/tokens.g.dart';

import '../support/design_system_harness.dart';
import '../support/fake_api.dart';

/// Synthetic screen that only uses the theme, to check what KdTheme gives
/// every screen: colors, type, buttons and fields.
class _ThemeSample extends StatelessWidget {
  const _ThemeSample();

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final t = KdTokens.of(context);
    return Scaffold(
      appBar: AppBar(title: const Text('Muestra del tema')),
      body: Padding(
        padding: EdgeInsets.all(t.space4),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text('Título de sección', style: text.titleLarge),
            SizedBox(height: t.space2),
            Text('Texto de cuerpo con datos ficticios.', style: text.bodyLarge),
            Text(
              'Texto secundario',
              style: text.bodySmall?.copyWith(color: t.textMuted),
            ),
            SizedBox(height: t.space4),
            const TextField(
              decoration: InputDecoration(
                labelText: 'Correo',
                border: OutlineInputBorder(),
              ),
            ),
            SizedBox(height: t.space4),
            FilledButton(onPressed: () {}, child: const Text('Continuar')),
            TextButton(onPressed: () {}, child: const Text('Cancelar')),
          ],
        ),
      ),
    );
  }
}

void main() {
  group('KdTheme', () {
    test('light and dark come from the generated tokens', () {
      expect(KdTheme.light.brightness, Brightness.light);
      expect(KdTheme.dark.brightness, Brightness.dark);
      expect(KdTheme.light.extension<KdTokens>(), same(KdTokens.light));
      expect(KdTheme.dark.extension<KdTokens>(), same(KdTokens.dark));
      expect(KdTheme.light.colorScheme.primary, KdTokens.light.colorAccent);
      expect(KdTheme.dark.colorScheme.primary, KdTokens.dark.colorAccent);
    });

    test('follows the operating system preference', () {
      expect(KdTheme.mode, ThemeMode.system);
    });

    testWidgets('the app uses KdTheme and follows the system', (tester) async {
      await pumpApp(tester);
      final app = tester.widget<MaterialApp>(find.byType(MaterialApp));
      expect(app.theme, same(KdTheme.light));
      expect(app.darkTheme, same(KdTheme.dark));
      expect(app.themeMode, ThemeMode.system);
    });

    testWidgets('the app picks the dark theme when the system is dark', (
      tester,
    ) async {
      tester.platformDispatcher.platformBrightnessTestValue = Brightness.dark;
      addTearDown(tester.platformDispatcher.clearPlatformBrightnessTestValue);
      await pumpApp(tester);
      final context = tester.element(find.byType(Scaffold).first);
      expect(KdTokens.of(context), same(KdTokens.dark));
    });
  });

  group('reduced motion', () {
    Future<BuildContext> pumpSample(WidgetTester tester) async {
      await tester.pumpWidget(themedApp(const _ThemeSample()));
      return tester.element(find.byType(_ThemeSample));
    }

    testWidgets('keeps the token durations by default', (tester) async {
      final context = await pumpSample(tester);
      expect(
        KdTheme.duration(context, KdConstants.motionDurationNormal),
        KdConstants.motionDurationNormal,
      );
      expect(KdTheme.reduceMotion(context), isFalse);
    });

    testWidgets('durations drop to zero when the system disables animations', (
      tester,
    ) async {
      simulateReducedMotion(tester);
      final context = await pumpSample(tester);
      expect(KdTheme.reduceMotion(context), isTrue);
      expect(
        KdTheme.duration(context, KdConstants.motionDurationSlow),
        Duration.zero,
      );
    });

    Future<Offset> pageOffsetMidTransition(WidgetTester tester) async {
      await tester.pumpWidget(themedApp(const _ThemeSample()));
      final navigator = tester.state<NavigatorState>(find.byType(Navigator));
      navigator.push(
        MaterialPageRoute<void>(
          builder: (_) => const Scaffold(body: Text('Segunda vista')),
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 50));
      return tester.getTopLeft(find.byType(Scaffold).last);
    }

    testWidgets('page transitions animate by default', (tester) async {
      final offset = await pageOffsetMidTransition(tester);
      expect(offset, isNot(Offset.zero));
      await tester.pumpAndSettle();
    });

    testWidgets('page transitions are instant with reduced motion', (
      tester,
    ) async {
      simulateReducedMotion(tester);
      final offset = await pageOffsetMidTransition(tester);
      expect(offset, Offset.zero);
      await tester.pumpAndSettle();
    });
  });

  testA11yGuidelines('theme sample', () => const _ThemeSample());

  testGoldens('theme_sample', () => const _ThemeSample());
}
