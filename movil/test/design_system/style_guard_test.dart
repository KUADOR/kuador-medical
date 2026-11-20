import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import '../support/style_guard.dart';

/// ADR-007: screens never write colors, spacing or radii by hand; they read
/// them from the design system (`lib/design_system/`).
void main() {
  group('style guard rules', () {
    List<String> rules(String source) =>
        scanSource('lib/sample.dart', source).map((v) => v.rule).toList();

    test('rejects hex color literals', () {
      expect(rules('final c = Color(0xFF00695C);'), ['color-literal']);
      expect(rules('const c = Color( 0xff000000 );'), ['color-literal']);
      expect(rules('final c = Color.fromARGB(255, 0, 0, 0);'), [
        'color-literal',
      ]);
      expect(rules('final c = Color.fromRGBO(0, 0, 0, 1);'), ['color-literal']);
    });

    test('rejects Material and Cupertino named colors', () {
      expect(rules('final c = Colors.red;'), ['named-color']);
      expect(rules('final c = Colors.transparent;'), ['named-color']);
      expect(rules('final c = CupertinoColors.systemBlue;'), ['named-color']);
    });

    test('rejects EdgeInsets built with numeric literals', () {
      expect(rules('const p = EdgeInsets.all(16);'), ['spacing-literal']);
      expect(rules('const p = EdgeInsets.fromLTRB(16, 8, 16, 0);'), [
        'spacing-literal',
      ]);
      expect(rules('const p = EdgeInsets.symmetric(vertical: 4.5);'), [
        'spacing-literal',
      ]);
      expect(rules('const p = EdgeInsetsDirectional.only(start: 8);'), [
        'spacing-literal',
      ]);
      expect(rules('final p = EdgeInsets.all(KdConstants.space4 * 2);'), [
        'spacing-literal',
      ]);
    });

    test('rejects BorderRadius and Radius built with numeric literals', () {
      expect(rules('final r = BorderRadius.circular(12);'), ['radius-literal']);
      expect(rules('final r = BorderRadius.all(Radius.circular(8));'), [
        'radius-literal',
        'radius-literal',
      ]);
      expect(rules('const r = Radius.elliptical(4, 8);'), ['radius-literal']);
    });

    test('accepts values that come from the tokens', () {
      expect(rules('const p = EdgeInsets.all(KdConstants.space4);'), isEmpty);
      expect(
        rules(
          'final p = EdgeInsets.fromLTRB(t.space4, t.space2, t.space4, t.space0);',
        ),
        isEmpty,
      );
      expect(
        rules('final r = BorderRadius.circular(KdConstants.radiusLg);'),
        isEmpty,
      );
      expect(rules('const p = EdgeInsets.zero;'), isEmpty);
      expect(rules('final c = scheme.primary;'), isEmpty);
      expect(rules('final c = KdTokens.of(context).colorAccent;'), isEmpty);
    });

    test('ignores comments and strings', () {
      expect(rules('// Colors.red and EdgeInsets.all(8)'), isEmpty);
      expect(rules('/* Color(0xFF000000) */ final a = 1;'), isEmpty);
      expect(rules("final s = 'Colors.red EdgeInsets.all(8)';"), isEmpty);
      expect(rules('final s = """\nColor(0xFF000000)\n""";'), isEmpty);
    });

    test('reports the line of each violation', () {
      final violations = scanSource(
        'lib/sample.dart',
        'import "x.dart";\n\nconst p = EdgeInsets.all(16);\n',
      );
      expect(violations.single.line, 3);
      expect(violations.single.toString(), contains('lib/sample.dart:3'));
    });
  });

  test('lib/ has no hand-written styles outside lib/design_system/', () {
    final violations = scanLib();
    expect(
      violations,
      isEmpty,
      reason:
          'Use the design system tokens (KdTokens.of(context) or KdConstants) '
          'instead of literals:\n${violations.join('\n')}',
    );
  });

  test('only lib/design_system/ may hold literal styles', () {
    final tokens = File('${designSystemDir}tokens.g.dart');
    expect(
      scanSource(tokens.path, tokens.readAsStringSync()),
      isNotEmpty,
      reason: 'the generated tokens are literal by design',
    );
    expect(scanLib().where((v) => v.path.startsWith(designSystemDir)), isEmpty);
  });
}
