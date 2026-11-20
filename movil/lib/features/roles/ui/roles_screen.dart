import 'package:flutter/material.dart';

import '../../../app_scope.dart';
import '../../../core/ui/module_widgets.dart';
import '../../../design_system/tokens.g.dart';
import '../data/role_models.dart';
import 'role_form_screen.dart';

/// Roles of the clinic (`GET /roles`), built over the fixed permission
/// catalog of Kuador (FR-016).
class RolesScreen extends StatefulWidget {
  const RolesScreen({super.key});

  @override
  State<RolesScreen> createState() => _RolesScreenState();
}

class _RolesScreenState extends State<RolesScreen> {
  Future<List<Role>>? _roles;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _roles ??= AppScope.of(context).rolesApi.listRoles();
  }

  void _reload() => setState(() {
    _roles = AppScope.of(context).rolesApi.listRoles();
  });

  Future<void> _open({Role? role}) async {
    final changed = await Navigator.of(
      context,
    ).push<bool>(MaterialPageRoute(builder: (_) => RoleFormScreen(role: role)));
    if (changed == true && mounted) _reload();
  }

  @override
  Widget build(BuildContext context) {
    final session = AppScope.of(context).session.session;
    final canCreate = session?.can('roles.create') ?? false;
    return ModulePage(
      title: 'Roles y permisos',
      floatingActionButton: canCreate
          ? FloatingActionButton.extended(
              onPressed: () => _open(),
              icon: const Icon(Icons.add),
              label: const Text('Nuevo rol'),
            )
          : null,
      body: FutureBuilder<List<Role>>(
        future: _roles,
        builder: (context, snapshot) {
          if (snapshot.hasError) {
            return LoadErrorView(error: snapshot.error!, onRetry: _reload);
          }
          final roles = snapshot.data;
          if (roles == null) return const LoadingView();
          return RefreshIndicator(
            onRefresh: () async => _reload(),
            child: ModuleListView(
              children: [
                const Text(
                  'Cada rol combina permisos del catálogo de Kuador. OWNER y ADMIN '
                  'están protegidos.',
                ),
                const SizedBox(height: KdConstants.space2),
                for (final role in roles)
                  Card(
                    child: ListTile(
                      title: Text(role.name),
                      subtitle: Text(_subtitle(role)),
                      leading: Icon(
                        role.systemRole
                            ? Icons.lock_outline
                            : Icons.admin_panel_settings_outlined,
                      ),
                      trailing: const Icon(Icons.chevron_right),
                      onTap: () => _open(role: role),
                    ),
                  ),
              ],
            ),
          );
        },
      ),
    );
  }

  static String _subtitle(Role role) {
    final parts = [
      if (role.systemRole) 'Protegido',
      if (role.clinical) 'Clínico',
      role.permissions.length == 1
          ? '1 permiso'
          : '${role.permissions.length} permisos',
    ];
    return parts.join(' · ');
  }
}
