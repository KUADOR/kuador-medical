import 'package:flutter/material.dart';

import '../../../app_scope.dart';
import '../../../core/api/api_exception.dart';
import '../../../core/api/error_messages.dart';
import '../../../core/connectivity/connectivity_widgets.dart';
import '../../../core/session/session_models.dart';
import '../../../core/ui/module_widgets.dart';
import '../../../design_system/tokens.g.dart';
import '../../auth/ui/auth_widgets.dart';
import '../../roles/data/role_models.dart';
import '../data/staff_models.dart';
import 'role_assignments_editor.dart';

/// Replaces the branch role assignments of a staff user
/// (`PUT /staff-users/{id}/role-assignments`). Pops the new assignments.
class RoleAssignmentsScreen extends StatefulWidget {
  const RoleAssignmentsScreen({super.key, required this.user});

  final StaffUser user;

  @override
  State<RoleAssignmentsScreen> createState() => _RoleAssignmentsScreenState();
}

class _RoleAssignmentsScreenState extends State<RoleAssignmentsScreen> {
  Future<List<Role>>? _roles;
  AssignmentDraft? _draft;
  bool _busy = false;
  String? _error;
  String? _fieldError;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _roles ??= AppScope.of(context).rolesApi.listRoles();
  }

  List<NamedRef> _branches(BuildContext context) => editorBranches(
    AppScope.of(context).session.session?.availableBranches ?? const [],
    widget.user.roleAssignments,
  );

  Future<void> _save(List<Role> roles) async {
    final request = _draft!.toRequest();
    if (request == null) {
      setState(() => _fieldError = 'Elige el rol en al menos una sucursal.');
      return;
    }
    if (_draft!.requiresProfessionalRegistration(roles) &&
        (widget.user.professionalRegistration ?? '').isEmpty) {
      setState(
        () => _fieldError =
            'Un rol clínico exige el registro profesional. Agrégalo primero en «Editar datos».',
      );
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
      _fieldError = null;
    });
    try {
      final assignments = await AppScope.of(
        context,
      ).staffApi.replaceAssignments(id: widget.user.id, request: request);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text(
            'Se guardaron los roles. Aplican en el siguiente acceso de la persona.',
          ),
        ),
      );
      Navigator.of(context).pop(assignments);
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _error = apiErrorMessage(e);
        _fieldError = apiFieldErrors(e).values.firstOrNull;
      });
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final session = AppScope.of(context).session.session;
    return ModulePage(
      title: 'Roles de ${widget.user.fullName}',
      body: FutureBuilder<List<Role>>(
        future: _roles,
        builder: (context, snapshot) {
          if (snapshot.hasError) {
            return LoadErrorView(
              error: snapshot.error!,
              onRetry: () => setState(() {
                _roles = AppScope.of(context).rolesApi.listRoles();
              }),
            );
          }
          final roles = snapshot.data;
          if (roles == null) return const LoadingView();
          final branches = _branches(context);
          final draft = _draft ??= AssignmentDraft.fromAssignments(
            widget.user.roleAssignments,
            branches,
          );
          return ModuleListView(
            children: [
              ErrorMessage(_error),
              RoleAssignmentsEditor(
                draft: draft,
                roles: roles,
                branches: branches,
                callerIsOwner: session?.isOwner ?? false,
                enabled: !_busy,
                errorText: _fieldError,
                onChanged: (d) => setState(() {
                  _draft = d;
                  _fieldError = null;
                }),
              ),
              const SizedBox(height: KdConstants.space6),
              OnlineSubmitButton(
                label: 'Guardar roles',
                busy: _busy,
                onPressed: () => _save(roles),
              ),
            ],
          );
        },
      ),
    );
  }
}
