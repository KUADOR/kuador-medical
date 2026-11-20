import 'package:flutter_test/flutter_test.dart';
import 'package:medical_kuador/config.dart';
import 'package:medical_kuador/core/config/api_config.dart';

void main() {
  group('ApiConfig', () {
    test('defaults to the local api seen from the Android emulator', () {
      expect(ApiConfig.defaultApiBaseUrl, 'http://10.0.2.2:8080');
      final config = ApiConfig.parse('');
      expect(config.origin, 'http://10.0.2.2:8080');
      expect(config.apiV1Url, 'http://10.0.2.2:8080/api/v1');
    });

    test(
      'accepts the api origin of the simulator and of a physical device',
      () {
        expect(
          ApiConfig.parse('http://localhost:8080').apiV1Url,
          'http://localhost:8080/api/v1',
        );
        expect(
          ApiConfig.parse(' http://192.168.1.50:8080/ ').apiV1Url,
          'http://192.168.1.50:8080/api/v1',
        );
      },
    );

    test('keeps a value that already includes /api/v1', () {
      final config = ApiConfig.parse('https://api.example.test/api/v1/');
      expect(config.origin, 'https://api.example.test');
      expect(config.apiV1Url, 'https://api.example.test/api/v1');
    });

    test('keeps a path prefix in front of /api/v1', () {
      expect(
        ApiConfig.parse('https://example.test/medk').apiV1Url,
        'https://example.test/medk/api/v1',
      );
    });

    test('rejects a value that is not an http or https URL', () {
      expect(() => ApiConfig.parse('10.0.2.2:8080'), throwsFormatException);
      expect(
        () => ApiConfig.parse('ftp://example.test'),
        throwsFormatException,
      );
      expect(() => ApiConfig.parse('http://'), throwsFormatException);
    });

    test('the error message never echoes the configured value', () {
      try {
        ApiConfig.parse('not a url with secret-looking-text');
        fail('expected a FormatException');
      } on FormatException catch (e) {
        expect(e.message, isNot(contains('secret-looking-text')));
        expect(e.message, contains('API_BASE_URL'));
      }
    });

    test('the build-time value is read from API_BASE_URL', () {
      // `flutter test` runs without --dart-define, so the default applies.
      expect(ApiConfig.fromEnvironment().origin, 'http://10.0.2.2:8080');
      expect(
        AppConfig.fromEnvironment().apiBaseUrl,
        'http://10.0.2.2:8080/api/v1',
      );
    });
  });
}
