import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:medical_kuador/design_system/tokens.g.dart';

void main() {
  group('generated tokens', () {
    test('light and dark ThemeData carry their KdTokens', () {
      expect(KdGeneratedTheme.light.brightness, Brightness.light);
      expect(KdGeneratedTheme.dark.brightness, Brightness.dark);
      expect(
        KdGeneratedTheme.light.extension<KdTokens>(),
        same(KdTokens.light),
      );
      expect(KdGeneratedTheme.dark.extension<KdTokens>(), same(KdTokens.dark));
    });

    test('the color scheme comes from the tokens', () {
      final scheme = KdGeneratedTheme.dark.colorScheme;
      expect(scheme.primary, KdTokens.dark.colorAccent);
      expect(scheme.onPrimary, KdTokens.dark.colorOnAccent);
      expect(scheme.surface, KdTokens.dark.surfaceRaised);
      expect(scheme.outlineVariant, KdTokens.dark.borderHairline.color);
      expect(
        KdGeneratedTheme.dark.scaffoldBackgroundColor,
        KdTokens.dark.surfaceBase,
      );
    });

    test('mode-independent tokens are shared constants', () {
      expect(KdTokens.light.space4, KdConstants.space4);
      expect(KdTokens.dark.space4, KdConstants.space4);
      expect(KdTokens.light.colorAccent, isNot(KdTokens.dark.colorAccent));
    });

    test('lerp goes from light to dark', () {
      expect(
        KdTokens.light.lerp(KdTokens.dark, 0).colorAccent,
        KdTokens.light.colorAccent,
      );
      expect(
        KdTokens.light.lerp(KdTokens.dark, 1).colorAccent,
        KdTokens.dark.colorAccent,
      );
      expect(
        KdTokens.light.lerp(KdTokens.dark, 1).motionEasingStandard,
        KdConstants.motionEasingStandard,
      );
      expect(KdTokens.light.lerp(null, 0.5), same(KdTokens.light));
    });

    test('copyWith replaces only the given tokens', () {
      final copy = KdTokens.light.copyWith(
        colorAccent: const Color(0xFF000000),
      );
      expect(copy.colorAccent, const Color(0xFF000000));
      expect(copy.surfaceRaised, KdTokens.light.surfaceRaised);
    });

    testWidgets('KdTokens.of reads the tokens of the active theme', (
      tester,
    ) async {
      late KdTokens tokens;
      await tester.pumpWidget(
        MaterialApp(
          theme: KdGeneratedTheme.light,
          darkTheme: KdGeneratedTheme.dark,
          themeMode: ThemeMode.dark,
          home: Builder(
            builder: (context) {
              tokens = KdTokens.of(context);
              return const SizedBox.shrink();
            },
          ),
        ),
      );
      expect(tokens.colorAccent, KdTokens.dark.colorAccent);
    });
  });
}
