/// Location of the api, provided at build time with
/// `--dart-define=API_BASE_URL=<url>`.
///
/// `API_BASE_URL` is the api origin, for example:
///
/// - Android emulator (default): `http://10.0.2.2:8080`
/// - iOS simulator: `http://localhost:8080`
/// - Physical device on the same network: `http://<LAN-IP>:8080`
///
/// A value that already ends in `/api/v1` is also accepted, so existing build
/// commands keep working.
class ApiConfig {
  const ApiConfig._(this.origin);

  /// The local api as seen from the Android emulator.
  static const String defaultApiBaseUrl = 'http://10.0.2.2:8080';

  static const String _apiV1Path = '/api/v1';

  /// Api origin (scheme, host, port and optional path prefix), without the
  /// `/api/v1` suffix and without a trailing slash.
  final String origin;

  /// Base URL of the `/api/v1` operations.
  String get apiV1Url => '$origin$_apiV1Path';

  /// Reads `API_BASE_URL` from the compile-time environment.
  factory ApiConfig.fromEnvironment() =>
      ApiConfig.parse(const String.fromEnvironment('API_BASE_URL'));

  /// Parses a configured value; blank means [defaultApiBaseUrl].
  ///
  /// Throws a [FormatException] that names the variable but never echoes its
  /// value.
  factory ApiConfig.parse(String raw) {
    final value = raw.trim().isEmpty ? defaultApiBaseUrl : raw.trim();
    final uri = Uri.tryParse(value);
    if (uri == null ||
        !(uri.scheme == 'http' || uri.scheme == 'https') ||
        uri.host.isEmpty ||
        uri.hasQuery ||
        uri.hasFragment) {
      throw const FormatException(
        'API_BASE_URL must be an http or https URL such as http://10.0.2.2:8080',
      );
    }
    var path = uri.path;
    while (path.endsWith('/')) {
      path = path.substring(0, path.length - 1);
    }
    if (path.endsWith(_apiV1Path)) {
      path = path.substring(0, path.length - _apiV1Path.length);
    }
    return ApiConfig._(uri.replace(path: path).toString());
  }
}
