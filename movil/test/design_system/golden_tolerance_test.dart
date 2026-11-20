import 'dart:io';
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../flutter_test_config.dart';

/// PNG of a 100x100 white square with [changed] pixels painted black.
Future<Uint8List> _png(int changed) async {
  final recorder = ui.PictureRecorder();
  final canvas = Canvas(recorder)
    ..drawRect(
      const Rect.fromLTWH(0, 0, 100, 100),
      Paint()..color = const Color(0xFFFFFFFF),
    );
  for (var i = 0; i < changed; i++) {
    canvas.drawRect(
      Rect.fromLTWH((i % 100).toDouble(), (i ~/ 100).toDouble(), 1, 1),
      Paint()..color = const Color(0xFF000000),
    );
  }
  final image = await recorder.endRecording().toImage(100, 100);
  final data = await image.toByteData(format: ui.ImageByteFormat.png);
  image.dispose();
  return data!.buffer.asUint8List();
}

void main() {
  test('every test file uses the tolerant golden comparator', () {
    expect(goldenFileComparator, isA<TolerantGoldenFileComparator>());
    expect(goldenDiffTolerance, lessThanOrEqualTo(0.01));
  });

  testWidgets('accepts antialiasing noise and rejects real changes', (
    tester,
  ) async {
    await tester.runAsync(() async {
      final dir = Directory.systemTemp.createTempSync('golden_tolerance_');
      addTearDown(() => dir.deleteSync(recursive: true));
      File('${dir.path}/base.png').writeAsBytesSync(await _png(0));
      final comparator = TolerantGoldenFileComparator(
        Uri.file('${dir.path}/sample_test.dart'),
      );
      final golden = Uri.parse('base.png');

      // 50 of 10 000 pixels (0.5 %), like the macOS/Linux difference.
      expect(await comparator.compare(await _png(50), golden), isTrue);
      // 500 of 10 000 pixels (5 %): a real style change fails.
      await expectLater(
        comparator.compare(await _png(500), golden),
        throwsA(isA<FlutterError>()),
      );
    });
  });
}
