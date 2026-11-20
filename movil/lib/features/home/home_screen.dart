import 'package:flutter/material.dart';

import '../../app_scope.dart';
import '../../core/api/api_exception.dart';
import '../../core/connectivity/connectivity_widgets.dart';
import '../../core/menu/app_menu.dart';
import '../../core/session/session_models.dart';
import '../../core/ui/module_widgets.dart';
import '../../design_system/layout.dart';
import '../../design_system/tokens.g.dart';
import '../../design_system/widgets/widgets.dart';
import '../../system_status/system_status_screen.dart';
import '../auth/ui/auth_messages.dart';
import '../patients/ui/my_guardian_links_screen.dart';
import '../patients/ui/patients_screen.dart';
import '../patients/ui/self_registration_settings_screen.dart';
import '../roles/ui/roles_screen.dart';
import '../staff_users/ui/staff_users_screen.dart';

/// Home of a signed-in account: clinic, active branch, branch switching and
/// the menu built from `GET /me`.
class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key, required this.session});

  final CurrentSession session;

  Future<void> _logout(BuildContext context) =>
      AppScope.of(context).session.logout();

  Future<void> _switchBranch(BuildContext context) async {
    final chosen = await showKdBottomSheet<NamedRef>(
      context: context,
      builder: (_) => _BranchPicker(session: session),
    );
    if (chosen == null || chosen.id == session.activeBranch?.id) return;
    if (!context.mounted) return;
    final messenger = ScaffoldMessenger.of(context);
    try {
      await AppScope.of(context).session.switchBranch(chosen.id);
      messenger.showSnackBar(
        SnackBar(content: Text('Ahora trabajas en ${chosen.name}.')),
      );
    } on ApiException catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(authErrorMessage(e))));
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = KdTokens.of(context);
    final menu = buildMenu(session);
    final online = ConnectivityScope.of(context).isOnline;
    return Scaffold(
      appBar: AppBar(
        title: Text(session.clinic?.name ?? 'Medical Kuador'),
        actions: [
          IconButton(
            tooltip: 'Cerrar sesión',
            icon: const KdIcon(KdIcons.logOut),
            onPressed: () => _logout(context),
          ),
        ],
      ),
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: KdLayout.pageMaxWidth),
            child: ListView(
              padding: const EdgeInsets.all(KdConstants.space4),
              children: [
                const OfflineBanner(),
                Semantics(
                  header: true,
                  child: Text(
                    'Hola, ${session.displayName}',
                    style: t.typeTitle.copyWith(color: t.textDefault),
                  ),
                ),
                if (session.roles.isNotEmpty) ...[
                  SizedBox(height: t.space1),
                  Text(
                    session.roles.join(' · '),
                    style: t.typeBodySm.copyWith(color: t.textMuted),
                  ),
                ],
                SizedBox(height: t.space4),
                if (session.activeBranch != null) ...[
                  KdListSection(
                    children: [
                      KdListTile(
                        leadingIcon: KdIcons.building,
                        title: 'Sucursal activa',
                        subtitle: session.activeBranch!.name,
                        trailing: session.canSwitchBranch
                            ? KdButton.text(
                                label: 'Cambiar',
                                onPressed: online
                                    ? () => _switchBranch(context)
                                    : null,
                              )
                            : null,
                      ),
                    ],
                  ),
                  SizedBox(height: t.space4),
                ],
                if (session.mustChangePassword) ...[
                  const NoticeCard(
                    'Debes cambiar tu contraseña antes de usar los módulos. '
                    'Hazlo desde la web de la clínica.',
                  ),
                  SizedBox(height: t.space4),
                ],
                if (menu.isEmpty && !session.mustChangePassword) ...[
                  const NoticeCard(
                    'Tu rol no tiene módulos habilitados en esta sucursal. '
                    'Consulta con el administrador de la clínica.',
                  ),
                  SizedBox(height: t.space4),
                ],
                if (menu.isNotEmpty) ...[
                  KdListSection(
                    children: [
                      for (final entry in menu)
                        KdListTile(
                          leadingIcon: entry.icon,
                          title: entry.label,
                          onTap: () => Navigator.of(context).push(
                            MaterialPageRoute<void>(
                              builder: (_) => moduleScreen(entry.destination),
                            ),
                          ),
                        ),
                    ],
                  ),
                  SizedBox(height: t.space4),
                ],
                KdListSection(
                  children: [
                    KdListTile(
                      leadingIcon: KdIcons.settings,
                      title: 'Estado del sistema',
                      onTap: () => Navigator.of(context).push(
                        MaterialPageRoute<void>(
                          builder: (_) => SystemStatusScreen(
                            client: AppScope.of(context).systemInfoClient,
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _BranchPicker extends StatelessWidget {
  const _BranchPicker({required this.session});

  final CurrentSession session;

  @override
  Widget build(BuildContext context) {
    return KdSheet(
      title: 'Elige la sucursal',
      child: KdListSection(
        children: [
          for (final branch in session.availableBranches)
            KdListTile(
              leadingIcon: KdIcons.building,
              title: branch.name,
              selected: branch.id == session.activeBranch?.id,
              trailing: branch.id == session.activeBranch?.id
                  ? const KdIcon(KdIcons.check, semanticLabel: 'Activa')
                  : null,
              onTap: () => Navigator.of(context).pop(branch),
            ),
        ],
      ),
    );
  }
}

/// Screen behind each menu destination.
Widget moduleScreen(MenuDestination destination) => switch (destination) {
  MenuDestination.staffUsers => const StaffUsersScreen(),
  MenuDestination.roles => const RolesScreen(),
  MenuDestination.patients => const PatientsScreen(),
  MenuDestination.selfRegistrationSettings =>
    const SelfRegistrationSettingsScreen(),
  MenuDestination.myGuardianLinks => const MyGuardianLinksScreen(),
};
