import 'package:flutter/widgets.dart';

import 'config.dart';
import 'core/session/session_controller.dart';
import 'features/auth/data/auth_api.dart';
import 'features/patients/data/patients_api.dart';
import 'features/roles/data/roles_api.dart';
import 'features/staff_users/data/staff_api.dart';
import 'system_status/system_info_client.dart';

/// Services shared by the screens.
class AppServices {
  const AppServices({
    required this.config,
    required this.session,
    required this.authApi,
    required this.systemInfoClient,
    required this.staffApi,
    required this.rolesApi,
    required this.patientsApi,
    this.clock = DateTime.now,
  });

  final AppConfig config;
  final SessionController session;
  final AuthApi authApi;
  final SystemInfoClient systemInfoClient;
  final StaffApi staffApi;
  final RolesApi rolesApi;
  final PatientsApi patientsApi;

  /// Current instant; fixed in tests.
  final DateTime Function() clock;
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
