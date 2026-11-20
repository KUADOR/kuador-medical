import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';

/// Largest share of different pixels a golden may show and still pass.
///
/// Goldens rendered on macOS and on the Linux CI differ in antialiasing
/// (0.55 % measured on the theme sample). A real style change (a color, a
/// spacing, a radius) changes far more pixels. Plan MEDK-33, risk R5.
const goldenDiffTolerance = 0.01;

/// Runs before every test file: installs the tolerant golden comparator.
Future<void> testExecutable(FutureOr<void> Function() testMain) async {
  final current = goldenFileComparator;
  if (current is LocalFileComparator) {
    goldenFileComparator = TolerantGoldenFileComparator(
      Uri.parse('${current.basedir}placeholder_test.dart'),
    );
  }
  await testMain();
}

/// A [LocalFileComparator] that accepts up to [tolerance] different pixels.
class TolerantGoldenFileComparator extends LocalFileComparator {
  TolerantGoldenFileComparator(
    super.testFile, {
    this.tolerance = goldenDiffTolerance,
  });

  final double tolerance;

  @override
  Future<bool> compare(Uint8List imageBytes, Uri golden) async {
    final result = await GoldenFileComparator.compareLists(
      imageBytes,
      await getGoldenBytes(golden),
    );
    if (result.passed || result.diffPercent <= tolerance) {
      result.dispose();
      return true;
    }
    final error = await generateFailureOutput(result, golden, basedir);
    result.dispose();
    throw FlutterError(error);
  }
}
