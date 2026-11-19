import 'package:flutter/widgets.dart';

import 'config.dart';
import 'core/session/session_controller.dart';
import 'features/auth/data/auth_api.dart';
import 'system_status/system_info_client.dart';

/// Services shared by the screens.
class AppServices {
  const AppServices({
    required this.config,
    required this.session,
    required this.authApi,
    required this.systemInfoClient,
  });

  final AppConfig config;
  final SessionController session;
  final AuthApi authApi;
  final SystemInfoClient systemInfoClient;
}

class AppScope extends InheritedWidget {
  const AppScope({super.key, required this.services, required super.child});

  final AppServices services;

  static AppServices of(BuildContext context) {
    final scope = context.getInheritedWidgetOfExactType<AppScope>();
    assert(scope != null, 'No AppScope above this widget');
    return scope!.services;
  }

  @override
  bool updateShouldNotify(AppScope oldWidget) => services != oldWidget.services;
}
