/// Build-time configuration, provided with `--dart-define`.
///
/// Example:
/// `flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080/api/v1`
class AppConfig {
  const AppConfig({
    required this.apiBaseUrl,
    this.appLinkHost = '',
    this.consentTextVersion = defaultConsentTextVersion,
  });

  /// Default used when `API_BASE_URL` is not provided: a local API.
  static const String defaultApiBaseUrl = 'http://localhost:8080/api/v1';

  /// Version of the personal data consent text accepted on self-registration.
  /// The text itself is [POR DEFINIR] (glossary); the version follows the one
  /// of the contract example until it is defined.
  static const String defaultConsentTextVersion = '2026-01';

  /// Base URL of the API, including the `/api/v1` prefix.
  final String apiBaseUrl;

  /// Host of the deep link domain (`KUADOR_APP_LINK_BASE_URL` of the api,
  /// without scheme). Empty: links of any `https` host are accepted, which is
  /// only meant for local builds.
  final String appLinkHost;

  final String consentTextVersion;

  /// Reads the configuration from the compile-time environment.
  factory AppConfig.fromEnvironment() {
    const apiBaseUrl = String.fromEnvironment(
      'API_BASE_URL',
      defaultValue: defaultApiBaseUrl,
    );
    const appLinkHost = String.fromEnvironment('APP_LINK_HOST');
    const consent = String.fromEnvironment(
      'CONSENT_TEXT_VERSION',
      defaultValue: defaultConsentTextVersion,
    );
    return AppConfig(
      apiBaseUrl: apiBaseUrl.trim().isEmpty
          ? defaultApiBaseUrl
          : apiBaseUrl.trim(),
      appLinkHost: appLinkHost.trim().toLowerCase(),
      consentTextVersion: consent.trim().isEmpty
          ? defaultConsentTextVersion
          : consent.trim(),
    );
  }
}
