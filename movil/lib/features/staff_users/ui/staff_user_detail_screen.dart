import 'package:flutter/material.dart';

import '../../../app_scope.dart';
import '../../../core/ui/module_widgets.dart';
import '../../../design_system/tokens.g.dart';
import '../../../design_system/widgets/widgets.dart';
import '../data/staff_models.dart';
import 'role_assignments_screen.dart';
import 'staff_user_form_screen.dart';

/// A staff user: data, status, branch roles and the actions the active
/// assignment allows (edit, change roles, resend invitation, disable/enable).
/// Nobody acts on their own record (FR-017).
class StaffUserDetailScreen extends StatefulWidget {
  const StaffUserDetailScreen({super.key, required this.userId});

  final String userId;

  @override
  State<StaffUserDetailScreen> createState() => _StaffUserDetailScreenState();
}

class _StaffUserDetailScreenState extends State<StaffUserDetailScreen> {
  Future<StaffUser>? _user;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _user ??= AppScope.of(context).staffApi.get(widget.userId);
  }

  void _reload() => setState(() {
    _user = AppScope.of(context).staffApi.get(widget.userId);
  });

  void _show(StaffUser user) => setState(() {
    _user = Future.value(user);
  });

  Future<void> _edit(StaffUser user) async {
    final saved = await Navigator.of(context).push<StaffUser>(
      MaterialPageRoute(builder: (_) => StaffUserFormScreen(user: user)),
    );
    if (saved != null && mounted) _show(saved);
  }

  Future<void> _changeRoles(StaffUser user) async {
    final assignments = await Navigator.of(context).push<List<RoleAssignment>>(
      MaterialPageRoute(builder: (_) => RoleAssignmentsScreen(user: user)),
    );
    if (assignments != null && mounted) _reload();
  }

  Future<void> _resend(StaffUser user) async {
    final api = AppScope.of(context).staffApi;
    final ok = await runOnline(
      context,
      () => api.resendInvitation(user.id),
      success: 'Se envió una nueva invitación. Las anteriores dejan de valer.',
    );
    if (ok && mounted) _reload();
  }

  Future<void> _toggle(StaffUser user) async {
    final disabling = user.status != AccountStatus.disabled;
    final confirmed = await confirmAction(
      context,
      title: disabling ? 'Deshabilitar acceso' : 'Habilitar acceso',
      message: disabling
          ? '${user.fullName} no podrá ingresar y sus sesiones abiertas se cerrarán. '
                'Sus datos e historial se conservan.'
          : '${user.fullName} podrá ingresar otra vez con sus roles actuales.',
      confirmLabel: disabling ? 'Deshabilitar' : 'Habilitar',
    );
    if (!confirmed || !mounted) return;
    final api = AppScope.of(context).staffApi;
    StaffUser? updated;
    await runOnline(
      context,
      () async => updated = disabling
          ? await api.disable(user.id)
          : await api.enable(user.id),
      success: disabling
          ? 'Se deshabilitó el acceso.'
          : 'Se habilitó el acceso.',
    );
    if (updated != null && mounted) _show(updated!);
  }

  @override
  Widget build(BuildContext context) {
    final session = AppScope.of(context).session.session;
    return ModulePage(
      title: 'Personal',
      body: FutureBuilder<StaffUser>(
        future: _user,
        builder: (context, snapshot) {
          if (snapshot.hasError) {
            return LoadErrorView(error: snapshot.error!, onRetry: _reload);
          }
          final user = snapshot.data;
          if (user == null) return const LoadingView();
          final self = session?.accountId == user.id;
          final canManage =
              !self && (session?.can('identity.users.manage') ?? false);
          final canAssign =
              !self && (session?.can('identity.assignments.manage') ?? false);
          final expires = user.invitationExpiresAt;
          return ModuleListView(
            children: [
              Semantics(
                header: true,
                child: Text(
                  user.fullName,
                  style: Theme.of(context).textTheme.headlineSmall,
                ),
              ),
              const SizedBox(height: KdConstants.space2),
              Wrap(
                spacing: KdConstants.space2,
                children: [Chip(label: Text(user.status.label))],
              ),
              InfoRow('Correo', user.email),
              InfoRow('Puesto', user.jobTitle),
              InfoRow(
                'Registro profesional',
                user.professionalRegistration ?? 'No registrado',
              ),
              if (user.status == AccountStatus.invited && expires != null)
                InfoRow('La invitación vence', formatDateTime(expires)),
              const SectionTitle('Roles por sucursal'),
              Text(describeAssignments(user.roleAssignments)),
              if (self)
                const Padding(
                  padding: EdgeInsets.only(top: KdConstants.space4),
                  child: NoticeCard(
                    'Esta es tu cuenta. Nadie puede cambiar sus propios datos, roles ni acceso.',
                  ),
                ),
              const SizedBox(height: KdConstants.space6),
              if (canManage)
                _Action(
                  icon: KdIcons.pencil,
                  label: 'Editar datos',
                  onPressed: () => _edit(user),
                ),
              if (canAssign)
                _Action(
                  icon: KdIcons.lock,
                  label: 'Cambiar roles',
                  onPressed: () => _changeRoles(user),
                ),
              if (canManage && user.status == AccountStatus.invited)
                _Action(
                  icon: KdIcons.mail,
                  label: 'Reenviar invitación',
                  onPressed: () => _resend(user),
                ),
              if (canManage)
                _Action(
                  icon: user.status == AccountStatus.disabled
                      ? KdIcons.check
                      : KdIcons.x,
                  label: user.status == AccountStatus.disabled
                      ? 'Habilitar acceso'
                      : 'Deshabilitar acceso',
                  onPressed: () => _toggle(user),
                ),
            ],
          );
        },
      ),
    );
  }
}

class _Action extends StatelessWidget {
  const _Action({
    required this.icon,
    required this.label,
    required this.onPressed,
  });

  final KdIconData icon;
  final String label;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: KdConstants.space3),
      child: KdButton.secondary(
        label: label,
        icon: icon,
        expand: true,
        onPressed: onPressed,
      ),
    );
  }
}
