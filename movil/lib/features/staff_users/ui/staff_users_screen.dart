import 'package:flutter/material.dart';

import '../../../app_scope.dart';
import '../../../core/ui/module_widgets.dart';
import '../../../core/ui/paged_search_list.dart';
import '../data/staff_models.dart';
import 'staff_user_detail_screen.dart';
import 'staff_user_form_screen.dart';

/// Staff of the clinic (`GET /staff-users`) with search and status filter.
class StaffUsersScreen extends StatefulWidget {
  const StaffUsersScreen({super.key});

  @override
  State<StaffUsersScreen> createState() => _StaffUsersScreenState();
}

class _StaffUsersScreenState extends State<StaffUsersScreen> {
  AccountStatus? _status;
  int _reloads = 0;

  Future<void> _invite() async {
    final created = await Navigator.of(context).push<StaffUser>(
      MaterialPageRoute(builder: (_) => const StaffUserFormScreen()),
    );
    if (created != null && mounted) setState(() => _reloads++);
  }

  Future<void> _open(StaffUser user) async {
    await Navigator.of(context).push<void>(
      MaterialPageRoute(builder: (_) => StaffUserDetailScreen(userId: user.id)),
    );
    if (mounted) setState(() => _reloads++);
  }

  @override
  Widget build(BuildContext context) {
    final services = AppScope.of(context);
    final canManage =
        services.session.session?.can('identity.users.manage') ?? false;
    return ModulePage(
      title: 'Personal',
      floatingActionButton: canManage
          ? FloatingActionButton.extended(
              onPressed: _invite,
              icon: const Icon(Icons.person_add_alt_1_outlined),
              label: const Text('Invitar'),
            )
          : null,
      body: PagedSearchList<StaffUser>(
        reloadKey: (_status, _reloads),
        searchLabel: 'Buscar por nombre o correo',
        emptyMessage: 'No hay personal que coincida con la búsqueda.',
        fetch: (query, page) =>
            services.staffApi.list(query: query, status: _status, page: page),
        header: DropdownButtonFormField<AccountStatus?>(
          initialValue: _status,
          isExpanded: true,
          items: [
            const DropdownMenuItem(
              value: null,
              child: Text('Todos los estados'),
            ),
            for (final s in AccountStatus.values)
              DropdownMenuItem(value: s, child: Text(s.label)),
          ],
          onChanged: (v) => setState(() => _status = v),
          decoration: fieldDecoration('Estado'),
        ),
        itemBuilder: (context, user) => Card(
          child: ListTile(
            title: Text(user.fullName),
            subtitle: Text(
              '${user.jobTitle} · ${user.status.label}\n'
              '${describeAssignments(user.roleAssignments)}',
            ),
            isThreeLine: true,
            trailing: const Icon(Icons.chevron_right),
            onTap: () => _open(user),
          ),
        ),
      ),
    );
  }
}
