import 'package:flutter/material.dart';

import '../../../core/session/session_models.dart';
import '../../roles/data/role_models.dart';
import '../data/staff_models.dart';

/// Chooses the roles of a staff user per branch (FR-026): the same role in
/// every branch (default) or one role per branch, with "no access" allowed in
/// some of them.
class RoleAssignmentsEditor extends StatelessWidget {
  const RoleAssignmentsEditor({
    super.key,
    required this.draft,
    required this.roles,
    required this.branches,
    required this.callerIsOwner,
    required this.onChanged,
    this.enabled = true,
    this.errorText,
  });

  final AssignmentDraft draft;
  final List<Role> roles;
  final List<NamedRef> branches;
  final bool callerIsOwner;
  final ValueChanged<AssignmentDraft> onChanged;
  final bool enabled;
  final String? errorText;

  static const String _noAccess = '';

  String _label(Role role) =>
      role.clinical ? '${role.name} (clínico)' : role.name;

  @override
  Widget build(BuildContext context) {
    final options = assignableRoles(
      roles,
      callerIsOwner: callerIsOwner,
      mode: draft.mode,
    );
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Semantics(
          header: true,
          child: Text('Roles por sucursal', style: theme.textTheme.titleMedium),
        ),
        const SizedBox(height: 4),
        const Text(
          'Por defecto la persona tiene el mismo rol en todas las sucursales. '
          'Puedes darle un rol distinto en cada una (por ejemplo, ADMIN en la '
          'matriz y Médico en otra).',
        ),
        RadioGroup<AssignmentMode>(
          groupValue: draft.mode,
          onChanged: (mode) {
            if (!enabled || mode == null || mode == draft.mode) return;
            onChanged(draft.copyWith(mode: mode));
          },
          child: Column(
            children: [
              RadioListTile<AssignmentMode>(
                value: AssignmentMode.allBranches,
                enabled: enabled,
                contentPadding: EdgeInsets.zero,
                title: const Text('El mismo rol en todas las sucursales'),
              ),
              RadioListTile<AssignmentMode>(
                value: AssignmentMode.perBranch,
                enabled: enabled,
                contentPadding: EdgeInsets.zero,
                title: const Text('Un rol distinto por sucursal'),
              ),
            ],
          ),
        ),
        const SizedBox(height: 8),
        if (draft.mode == AssignmentMode.allBranches)
          DropdownButtonFormField<String>(
            key: ValueKey('all-${draft.roleId}'),
            initialValue: options.any((r) => r.id == draft.roleId)
                ? draft.roleId
                : null,
            isExpanded: true,
            items: [
              for (final r in options)
                DropdownMenuItem(value: r.id, child: Text(_label(r))),
            ],
            onChanged: enabled
                ? (v) => onChanged(draft.copyWith(roleId: v))
                : null,
            decoration: InputDecoration(
              labelText: 'Rol',
              border: const OutlineInputBorder(),
              errorText: errorText,
            ),
          )
        else ...[
          for (final branch in branches) ...[
            DropdownButtonFormField<String>(
              key: ValueKey('branch-${branch.id}-${draft.byBranch[branch.id]}'),
              initialValue:
                  options.any((r) => r.id == draft.byBranch[branch.id])
                  ? draft.byBranch[branch.id]
                  : _noAccess,
              isExpanded: true,
              items: [
                const DropdownMenuItem(
                  value: _noAccess,
                  child: Text('Sin acceso'),
                ),
                for (final r in options)
                  DropdownMenuItem(value: r.id, child: Text(_label(r))),
              ],
              onChanged: enabled
                  ? (v) => onChanged(
                      draft.withBranchRole(
                        branch.id,
                        (v == null || v == _noAccess) ? null : v,
                      ),
                    )
                  : null,
              decoration: InputDecoration(
                labelText: 'Rol en ${branch.name}',
                border: const OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 12),
          ],
          if (errorText != null)
            Semantics(
              liveRegion: true,
              child: Text(
                errorText!,
                style: TextStyle(color: theme.colorScheme.error),
              ),
            ),
        ],
      ],
    );
  }
}
