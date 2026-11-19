/// Build-time configuration, provided with `--dart-define`.
///
/// Example:
/// `flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080/api/v1`
class AppConfig {
  const AppConfig({required this.apiBaseUrl});

  /// Default used when `API_BASE_URL` is not provided: a local API.
  static const String defaultApiBaseUrl = 'http://localhost:8080/api/v1';

  /// Base URL of the API, including the `/api/v1` prefix.
  final String apiBaseUrl;

  /// Reads the configuration from the compile-time environment.
  factory AppConfig.fromEnvironment() {
    const value = String.fromEnvironment(
      'API_BASE_URL',
      defaultValue: defaultApiBaseUrl,
    );
    return AppConfig(
      apiBaseUrl: value.trim().isEmpty ? defaultApiBaseUrl : value.trim(),
    );
  }
}
