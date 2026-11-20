import 'package:flutter/material.dart';

import '../../../app_scope.dart';
import '../../../core/api/api_exception.dart';
import '../../../core/api/error_messages.dart';
import '../../../core/connectivity/connectivity_widgets.dart';
import '../../../core/ui/module_widgets.dart';
import '../../../core/util/idempotent_creation.dart';
import '../../../design_system/tokens.g.dart';
import '../../auth/ui/auth_widgets.dart';
import '../data/role_models.dart';

/// Creates a role or shows and edits an existing one. Permissions the person
/// does not hold cannot be added (anti-escalation, FR-017); OWNER is
/// read-only and only an OWNER edits ADMIN. The API enforces every rule.
///
/// Pops `true` when the role changed.
class RoleFormScreen extends StatefulWidget {
  const RoleFormScreen({super.key, this.role});

  /// Null to create a new role.
  final Role? role;

  @override
  State<RoleFormScreen> createState() => _RoleFormScreenState();
}

class _RoleFormScreenState extends State<RoleFormScreen> {
  final _formKey = GlobalKey<FormState>();
  late final _name = TextEditingController(text: widget.role?.name ?? '');
  late final _description = TextEditingController(
    text: widget.role?.description ?? '',
  );
  late bool _clinical = widget.role?.clinical ?? false;
  late final Set<String> _selected = {...?widget.role?.permissions};
  final _creation = IdempotentCreation();

  Future<List<PermissionInfo>>? _catalog;
  bool _busy = false;
  String? _error;
  Map<String, String> _fieldErrors = const {};

  bool get _isNew => widget.role == null;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _catalog ??= AppScope.of(context).rolesApi.listPermissions();
  }

  @override
  void dispose() {
    _name.dispose();
    _description.dispose();
    super.dispose();
  }

  /// Why the role cannot be edited, or null when it can.
  String? _readOnlyReason() {
    final role = widget.role;
    final session = AppScope.of(context).session.session;
    if (role == null) return null;
    if (role.isOwner) {
      return 'El rol OWNER está protegido: tiene todos los permisos y no se puede editar.';
    }
    if (role.isAdmin && !(session?.isOwner ?? false)) {
      return 'Solo una persona con el rol OWNER puede editar el rol ADMIN.';
    }
    if (!(session?.can('roles.update') ?? false)) {
      return 'Tu rol no permite editar roles. Puedes ver sus permisos.';
    }
    return null;
  }

  RoleDraft _draft() => RoleDraft(
    name: _name.text.trim(),
    description: _description.text.trim(),
    clinical: _clinical,
    permissions: _selected,
  );

  Future<void> _save() async {
    setState(() => _fieldErrors = const {});
    if (_busy || !_formKey.currentState!.validate()) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    final api = AppScope.of(context).rolesApi;
    final draft = _draft();
    try {
      if (_isNew) {
        await api.createRole(id: _creation.idFor(draft.toJson()), draft: draft);
        _creation.completed();
      } else {
        await api.updateRole(
          id: widget.role!.id,
          draft: draft,
          version: widget.role!.version,
        );
      }
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(_isNew ? 'Se creó el rol.' : 'Se guardó el rol.'),
        ),
      );
      Navigator.of(context).pop(true);
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _error = apiErrorMessage(e);
        _fieldErrors = apiFieldErrors(e);
      });
      _formKey.currentState!.validate();
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _delete() async {
    final role = widget.role!;
    final confirmed = await confirmAction(
      context,
      title: 'Eliminar rol',
      message:
          '¿Eliminar el rol ${role.name}? Solo se puede eliminar si nadie lo tiene asignado.',
      confirmLabel: 'Eliminar',
    );
    if (!confirmed || !mounted) return;
    final api = AppScope.of(context).rolesApi;
    final navigator = Navigator.of(context);
    final ok = await runOnline(
      context,
      () => api.deleteRole(role.id),
      success: 'Se eliminó el rol.',
    );
    if (ok) navigator.pop(true);
  }

  @override
  Widget build(BuildContext context) {
    final session = AppScope.of(context).session.session;
    final held = session?.permissions ?? const <String>{};
    final readOnlyReason = _readOnlyReason();
    final readOnly = readOnlyReason != null;
    final role = widget.role;
    final title = _isNew
        ? 'Nuevo rol'
        : (readOnly ? 'Rol ${role!.name}' : 'Editar rol ${role!.name}');
    final canDelete =
        role != null &&
        !role.systemRole &&
        (session?.can('roles.delete') ?? false);

    return ModulePage(
      title: title,
      body: FutureBuilder<List<PermissionInfo>>(
        future: _catalog,
        builder: (context, snapshot) {
          if (snapshot.hasError) {
            return LoadErrorView(
              error: snapshot.error!,
              onRetry: () => setState(() {
                _catalog = AppScope.of(context).rolesApi.listPermissions();
              }),
            );
          }
          final catalog = snapshot.data;
          if (catalog == null) return const LoadingView();
          final options = _withUnknownCodes(catalog);
          final initial = role?.permissions ?? const <String>{};
          bool locked(String code) =>
              !held.contains(code) && !initial.contains(code);
          final modules = <String, List<PermissionInfo>>{};
          for (final p in options) {
            modules.putIfAbsent(p.module, () => []).add(p);
          }
          return ModuleListView(
            children: [
              if (readOnlyReason != null) NoticeCard(readOnlyReason),
              ErrorMessage(_error),
              Form(
                key: _formKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    const SizedBox(height: 8),
                    TextFormField(
                      controller: _name,
                      readOnly: readOnly,
                      textCapitalization: TextCapitalization.sentences,
                      validator: (v) =>
                          FormRules.requiredMax(80)(v) ?? _fieldErrors['name'],
                      decoration: fieldDecoration('Nombre del rol'),
                    ),
                    const SizedBox(height: 16),
                    TextFormField(
                      controller: _description,
                      readOnly: readOnly,
                      maxLines: 3,
                      minLines: 1,
                      validator: (v) =>
                          FormRules.optionalMax(500)(v) ??
                          _fieldErrors['description'],
                      decoration: fieldDecoration('Descripción (opcional)'),
                    ),
                    SwitchListTile(
                      value: _clinical,
                      contentPadding: EdgeInsets.zero,
                      onChanged: readOnly
                          ? null
                          : (v) => setState(() => _clinical = v),
                      title: const Text('Rol clínico'),
                      subtitle: const Text(
                        'Quien tenga este rol debe registrar su número de registro profesional.',
                      ),
                    ),
                  ],
                ),
              ),
              const SectionTitle('Permisos'),
              if (!readOnly)
                const Text(
                  'Solo puedes agregar permisos que tú tienes. Quitar un permiso siempre está permitido.',
                ),
              if (_fieldErrors['permissions'] != null)
                ErrorMessage(_fieldErrors['permissions']),
              for (final entry in modules.entries) ...[
                Padding(
                  padding: const EdgeInsets.only(top: KdConstants.space3),
                  child: Semantics(
                    header: true,
                    child: Text(
                      moduleLabel(entry.key),
                      style: Theme.of(context).textTheme.titleSmall,
                    ),
                  ),
                ),
                for (final p in entry.value)
                  CheckboxListTile(
                    value: _selected.contains(p.code),
                    contentPadding: EdgeInsets.zero,
                    controlAffinity: ListTileControlAffinity.leading,
                    onChanged:
                        readOnly ||
                            (locked(p.code) && !_selected.contains(p.code))
                        ? null
                        : (v) => setState(
                            () => v == true
                                ? _selected.add(p.code)
                                : _selected.remove(p.code),
                          ),
                    title: Text(p.description),
                    subtitle: locked(p.code)
                        ? const Text('No tienes este permiso')
                        : null,
                  ),
              ],
              const SizedBox(height: 24),
              if (!readOnly)
                OnlineSubmitButton(
                  label: _isNew ? 'Crear rol' : 'Guardar cambios',
                  busy: _busy,
                  onPressed: _save,
                ),
              if (canDelete) ...[
                const SizedBox(height: 12),
                OutlinedButton.icon(
                  onPressed: _busy ? null : _delete,
                  icon: const Icon(Icons.delete_outline),
                  label: const Text('Eliminar rol'),
                  style: OutlinedButton.styleFrom(
                    minimumSize: const Size.fromHeight(48),
                  ),
                ),
              ],
            ],
          );
        },
      ),
    );
  }

  /// Catalog plus any code the role has that the catalog does not list.
  List<PermissionInfo> _withUnknownCodes(List<PermissionInfo> catalog) {
    final known = {for (final p in catalog) p.code};
    return [
      ...catalog,
      for (final code in widget.role?.permissions ?? const <String>{})
        if (!known.contains(code))
          PermissionInfo(code: code, module: 'other', description: code),
    ];
  }
}
