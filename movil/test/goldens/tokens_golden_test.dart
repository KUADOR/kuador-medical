@Tags(['golden'])
library;

import 'dart:io' show Platform;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:medical_kuador/design_system/tokens.g.dart';

// Golden of the generated tokens in light and dark (MEDK-36). It renders no text, so the result
// does not depend on fonts. Baselines are rendered on Linux, like CI: see movil/README.md
// («Goldens»). A token change makes this test fail on purpose; review the new image and
// update the baseline in the same PR. On macOS and Windows the rendering differs slightly, so
// the comparison is skipped there instead of failing `flutter test` on every developer machine.

class _TokenSwatches extends StatelessWidget {
  const _TokenSwatches();

  @override
  Widget build(BuildContext context) {
    final tokens = KdTokens.of(context);
    final colors = <Color>[
      tokens.surfaceBase,
      tokens.surfaceSunken,
      tokens.surfaceRaised,
      tokens.surfaceOverlay,
      tokens.textDefault,
      tokens.textMuted,
      tokens.colorAccent,
      tokens.colorOnAccent,
      tokens.colorFocus,
      tokens.colorOk,
      tokens.colorError,
      tokens.colorWarning,
      tokens.colorInfo,
    ];
    return ColoredBox(
      color: tokens.surfaceBase,
      child: Padding(
        padding: EdgeInsets.all(tokens.space4),
        child: Wrap(
          spacing: tokens.space2,
          runSpacing: tokens.space2,
          children: [
            for (final color in colors)
              Container(
                width: tokens.sizeTargetMin,
                height: tokens.sizeTargetMin,
                decoration: BoxDecoration(
                  color: color,
                  borderRadius: BorderRadius.circular(tokens.radiusMd),
                  border: Border.fromBorderSide(tokens.borderHairline),
                ),
              ),
            for (final shadow in [
              tokens.shadowSm,
              tokens.shadowMd,
              tokens.shadowLg,
            ])
              Container(
                width: tokens.sizeTargetMin,
                height: tokens.sizeTargetMin,
                decoration: BoxDecoration(
                  color: tokens.surfaceRaised,
                  borderRadius: BorderRadius.circular(tokens.radiusLg),
                  boxShadow: shadow,
                ),
              ),
          ],
        ),
      ),
    );
  }
}

void main() {
  // testWidgets only takes a bool: baselines are Linux renderings (CI), see movil/README.md.
  final skipOutsideLinux = !Platform.isLinux;

  for (final (name, theme) in [
    ('light', KdGeneratedTheme.light),
    ('dark', KdGeneratedTheme.dark),
  ]) {
    testWidgets('token swatches ($name)', (tester) async {
      tester.view.physicalSize = const Size(400, 300);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);

      await tester.pumpWidget(
        MaterialApp(
          debugShowCheckedModeBanner: false,
          theme: theme,
          home: const _TokenSwatches(),
        ),
      );

      await expectLater(
        find.byType(_TokenSwatches),
        matchesGoldenFile('tokens_$name.png'),
      );
    }, skip: skipOutsideLinux);
  }
}
