import 'package:flutter/material.dart';

import '../../app_scope.dart';
import '../../core/api/api_exception.dart';
import '../../core/connectivity/connectivity_widgets.dart';
import '../../core/menu/app_menu.dart';
import '../../core/session/session_models.dart';
import '../../system_status/system_status_screen.dart';
import '../auth/ui/auth_messages.dart';

/// Home of a signed-in account: clinic, active branch, branch switching and
/// the menu built from `GET /me`.
class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key, required this.session});

  final CurrentSession session;

  Future<void> _logout(BuildContext context) =>
      AppScope.of(context).session.logout();

  Future<void> _switchBranch(BuildContext context) async {
    final chosen = await showModalBottomSheet<NamedRef>(
      context: context,
      showDragHandle: true,
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
    final theme = Theme.of(context);
    final menu = buildMenu(session);
    final online = ConnectivityScope.of(context).isOnline;
    return Scaffold(
      appBar: AppBar(
        title: Text(session.clinic?.name ?? 'Medical Kuador'),
        actions: [
          IconButton(
            tooltip: 'Cerrar sesión',
            icon: const Icon(Icons.logout),
            onPressed: () => _logout(context),
          ),
        ],
      ),
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 600),
            child: ListView(
              padding: const EdgeInsets.all(16),
              children: [
                const OfflineBanner(),
                Semantics(
                  header: true,
                  child: Text(
                    'Hola, ${session.displayName}',
                    style: theme.textTheme.headlineSmall,
                  ),
                ),
                if (session.roles.isNotEmpty) ...[
                  const SizedBox(height: 4),
                  Text(session.roles.join(' · ')),
                ],
                const SizedBox(height: 16),
                if (session.activeBranch != null)
                  Card(
                    child: ListTile(
                      leading: const Icon(Icons.store_mall_directory_outlined),
                      title: const Text('Sucursal activa'),
                      subtitle: Text(session.activeBranch!.name),
                      trailing: session.canSwitchBranch
                          ? TextButton(
                              onPressed: online
                                  ? () => _switchBranch(context)
                                  : null,
                              child: const Text('Cambiar'),
                            )
                          : null,
                    ),
                  ),
                if (session.mustChangePassword)
                  const _Notice(
                    'Debes cambiar tu contraseña antes de usar los módulos. '
                    'Hazlo desde la web de la clínica.',
                  ),
                const SizedBox(height: 8),
                if (menu.isEmpty && !session.mustChangePassword)
                  const _Notice(
                    'Tu rol no tiene módulos habilitados en esta sucursal. '
                    'Consulta con el administrador de la clínica.',
                  ),
                for (final entry in menu)
                  Card(
                    child: ListTile(
                      leading: Icon(entry.icon),
                      title: Text(entry.label),
                      trailing: const Icon(Icons.chevron_right),
                      onTap: () => Navigator.of(context).push(
                        MaterialPageRoute<void>(
                          builder: (_) => ModulePendingScreen(entry: entry),
                        ),
                      ),
                    ),
                  ),
                const SizedBox(height: 8),
                ListTile(
                  leading: const Icon(Icons.monitor_heart_outlined),
                  title: const Text('Estado del sistema'),
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
    return SafeArea(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Semantics(
            header: true,
            child: Text(
              'Elige la sucursal',
              style: Theme.of(context).textTheme.titleMedium,
            ),
          ),
          const SizedBox(height: 8),
          for (final branch in session.availableBranches)
            ListTile(
              leading: Icon(
                branch.id == session.activeBranch?.id
                    ? Icons.radio_button_checked
                    : Icons.radio_button_unchecked,
              ),
              title: Text(branch.name),
              selected: branch.id == session.activeBranch?.id,
              onTap: () => Navigator.of(context).pop(branch),
            ),
          const SizedBox(height: 8),
        ],
      ),
    );
  }
}

class _Notice extends StatelessWidget {
  const _Notice(this.text);

  final String text;

  @override
  Widget build(BuildContext context) {
    return Card(
      color: Theme.of(context).colorScheme.secondaryContainer,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            const Icon(Icons.info_outline),
            const SizedBox(width: 12),
            Expanded(child: Text(text)),
          ],
        ),
      ),
    );
  }
}

/// Placeholder for the module screens that arrive in MEDK-24.
class ModulePendingScreen extends StatelessWidget {
  const ModulePendingScreen({super.key, required this.entry});

  final MenuEntry entry;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(entry.label)),
      body: const SafeArea(
        child: Center(
          child: Padding(
            padding: EdgeInsets.all(24),
            child: Text(
              'Este módulo estará disponible en la app en una próxima versión. '
              'Mientras tanto, úsalo desde la web.',
              textAlign: TextAlign.center,
            ),
          ),
        ),
      ),
    );
  }
}
