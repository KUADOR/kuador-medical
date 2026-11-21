// Performance measurement for SC-011 (MEDK-48): time to the first useful screen and
// frame timings while scrolling a patient screen. Synthetic data only (FR-014): the api
// is the contract fake of test/support, so no network, tokens or personal data are used.
//
// Run on a reference device (specs/033-epica-nueva-identidad-visual-y-experienc/audit.md § 4.2)
// in profile mode, which is the only representative one:
//
//   flutter drive --profile \
//     --driver=test_driver/perf_driver.dart \
//     --target=integration_test/performance_test.dart -d <device-id>
//
// The driver writes build/integration_response_data.json and the timeline summary
// build/patient_contacts_scroll.timeline_summary.json (frame build/raster times, missed frames).
// Cold start is measured apart with `flutter run --profile --trace-startup` (audit.md § 4.2).
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

import '../test/support/fake_api.dart';
import '../test/support/module_fixtures.dart';

/// Enough synthetic links to make the screen scroll several viewports.
const _syntheticLinkCount = 40;

FakeApi _patientApi() => FakeApi()
  ..on('GET /me', (_) => json(patientMe()))
  ..on(
    'GET /me/guardian-links',
    (_) => json([
      for (var i = 0; i < _syntheticLinkCount; i++)
        guardianLink(
          id: '00000000-0000-4000-8000-${i.toString().padLeft(12, '0')}',
          current: i.isEven,
          endReason: i.isEven ? null : 'ADULTHOOD',
        ),
    ]),
  );

void main() {
  final binding = IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('time to the patient home with a restored session', (
    tester,
  ) async {
    final watch = Stopwatch()..start();
    await pumpApp(tester, api: _patientApi(), stored: storedTokens());
    await tester.pumpAndSettle();
    expect(find.text('Mis contactos autorizados'), findsWidgets);
    watch.stop();

    binding.reportData = {
      ...?binding.reportData,
      'patient_home_ready_ms': watch.elapsedMilliseconds,
    };
  });

  testWidgets('scrolling the patient contacts screen', (tester) async {
    await pumpApp(tester, api: _patientApi(), stored: storedTokens());
    await tapText(tester, 'Mis contactos autorizados');
    final list = find.byType(Scrollable).first;

    await binding.traceAction(() async {
      for (var i = 0; i < 3; i++) {
        await tester.fling(list, const Offset(0, -600), 2000);
        await tester.pumpAndSettle();
        await tester.fling(list, const Offset(0, 600), 2000);
        await tester.pumpAndSettle();
      }
    }, reportKey: 'patient_contacts_scroll');
  });
}
