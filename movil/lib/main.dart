import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;

import 'app.dart';
import 'app_scope.dart';
import 'config.dart';
import 'core/api/api_client.dart';
import 'core/connectivity/connectivity_monitor.dart';
import 'core/session/session_api.dart';
import 'core/session/session_controller.dart';
import 'core/session/token_store.dart';
import 'features/auth/data/auth_api.dart';
import 'features/auth/deep_links/deep_link.dart';
import 'system_status/system_info_client.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  final config = AppConfig.fromEnvironment();
  final httpClient = http.Client();
  runApp(
    MedicalKuadorApp(
      services: buildServices(
        config: config,
        httpClient: httpClient,
        tokenStore: SecureTokenStore(),
      ),
      connectivity: PlatformConnectivityMonitor(),
      deepLinks: PlatformDeepLinkSource(),
    ),
  );
}

/// Wires the services; tests call it with fakes.
AppServices buildServices({
  required AppConfig config,
  required http.Client httpClient,
  required TokenStore tokenStore,
  DateTime Function()? clock,
}) {
  final api = ApiClient(httpClient: httpClient, baseUrl: config.apiBaseUrl);
  final session = SessionController(
    api: SessionApi(api, clock: clock),
    store: tokenStore,
    clock: clock,
  );
  api.accessTokenProvider = session;
  return AppServices(
    config: config,
    session: session,
    authApi: AuthApi(api, clock: clock),
    systemInfoClient: SystemInfoClient(
      httpClient: httpClient,
      baseUrl: config.apiBaseUrl,
    ),
  );
}
