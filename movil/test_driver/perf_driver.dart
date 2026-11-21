// Host-side driver of integration_test/performance_test.dart (MEDK-48). It writes the
// reported data and a timeline summary per traced action into build/.
import 'package:flutter_driver/flutter_driver.dart' as driver;
import 'package:integration_test/integration_test_driver.dart';

Future<void> main() => integrationDriver(
  responseDataCallback: (data) async {
    if (data == null) return;
    for (final entry in data.entries) {
      final value = entry.value;
      if (value is Map<String, dynamic> && value.containsKey('traceEvents')) {
        final timeline = driver.Timeline.fromJson(value);
        await driver.TimelineSummary.summarize(
          timeline,
        ).writeTimelineToFile(entry.key, pretty: true, includeSummary: true);
      }
    }
    await writeResponseData(data);
  },
);
