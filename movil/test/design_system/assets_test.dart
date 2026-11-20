import 'dart:io';

import 'package:flutter_svg/flutter_svg.dart';
import 'package:flutter_test/flutter_test.dart';

/// The design system assets are declared and loadable (ADR-006).
void main() {
  final pubspec = File('pubspec.yaml').readAsStringSync();

  test('pubspec declares the icon and font asset folders', () {
    expect(pubspec, contains('- assets/icons/'));
    expect(pubspec, contains('- assets/fonts/'));
    expect(Directory('assets/icons').existsSync(), isTrue);
    expect(Directory('assets/fonts').existsSync(), isTrue);
  });

  test('flutter_svg is a dependency', () {
    expect(pubspec, matches(RegExp(r'^\s+flutter_svg:', multiLine: true)));
  });

  test('every icon is a valid SVG that flutter_svg can draw', () async {
    final icons = Directory(
      'assets/icons',
    ).listSync().whereType<File>().where((f) => f.path.endsWith('.svg'));
    for (final icon in icons) {
      final info = await vg.loadPicture(
        SvgStringLoader(icon.readAsStringSync()),
        null,
      );
      expect(info.size.isEmpty, isFalse, reason: icon.path);
      info.picture.dispose();
    }
  });

  testWidgets('flutter_svg draws an inline icon', (tester) async {
    const svg =
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">'
        '<path d="M4 12h16"/></svg>';
    await tester.pumpWidget(
      SvgPicture.string(svg, width: 24, height: 24, semanticsLabel: 'Icono'),
    );
    expect(find.byType(SvgPicture), findsOneWidget);
  });
}
