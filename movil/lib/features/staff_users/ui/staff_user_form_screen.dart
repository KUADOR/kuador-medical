import 'package:flutter/material.dart';

import '../../../app_scope.dart';
import '../../../core/api/api_exception.dart';
import '../../../core/api/error_messages.dart';
import '../../../core/connectivity/connectivity_widgets.dart';
import '../../../core/ui/module_widgets.dart';
import '../../../core/util/idempotent_creation.dart';
import '../../auth/ui/auth_widgets.dart';
import '../../roles/data/role_models.dart';
import '../data/staff_models.dart';
import 'role_assignments_editor.dart';
import '../../../design_system/tokens.g.dart';

/// Registers a staff member and sends the invitation (FR-002, FR-007), or
/// edits the personal data of an existing one. Requires a connection
/// (FR-029); creations carry a client id and `Idempotency-Key`.
///
/// Pops the saved [StaffUser].
class StaffUserFormScreen extends StatefulWidget {
  const StaffUserFormScreen({super.key, this.user});

  /// Null to register a new staff member.
  final StaffUser? user;

  @override
  State<StaffUserFormScreen> createState() => _StaffUserFormScreenState();
}

class _StaffUserFormScreenState extends State<StaffUserFormScreen> {
  final _formKey = GlobalKey<FormState>();
  late final _firstNames = TextEditingController(
    text: widget.user?.firstNames ?? '',
  );
  late final _lastNames = TextEditingController(
    text: widget.user?.lastNames ?? '',
  );
  final _email = TextEditingController();
  late final _jobTitle = TextEditingController(
    text: widget.user?.jobTitle ?? '',
  );
  late final _registration = TextEditingController(
    text: widget.user?.professionalRegistration ?? '',
  );
  final _creation = IdempotentCreation();

  Future<List<Role>>? _roles;
  AssignmentDraft _draft = const AssignmentDraft();
  bool _busy = false;
  String? _error;
  String? _assignmentsError;
  Map<String, String> _fieldErrors = const {};

  bool get _isNew => widget.user == null;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _roles ??= AppScope.of(context).rolesApi.listRoles();
  }

  @override
  void dispose() {
    for (final c in [
      _firstNames,
      _lastNames,
      _email,
      _jobTitle,
      _registration,
    ]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _submit(List<Role> roles) async {
    setState(() {
      _fieldErrors = const {};
      _assignmentsError = null;
    });
    final valid = _formKey.currentState!.validate();
    final assignments = _draft.toRequest();
    if (_isNew && assignments == null) {
      setState(
        () => _assignmentsError =
            'Elige el rol de la persona en al menos una sucursal.',
      );
    }
    if (_busy || !valid || (_isNew && assignments == null)) return;
    if (!_isNew && _changes().isEmpty) {
      setState(() => _error = 'No hay cambios para guardar.');
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    final api = AppScope.of(context).staffApi;
    try {
      final StaffUser saved;
      if (_isNew) {
        final draft = StaffUserDraft(
          firstNames: _firstNames.text.trim(),
          lastNames: _lastNames.text.trim(),
          email: _email.text.trim(),
          jobTitle: _jobTitle.text.trim(),
          professionalRegistration: _registration.text.trim(),
          assignments: assignments!,
        );
        saved = await api.create(
          id: _creation.idFor(draft.toJson()),
          draft: draft,
        );
        _creation.completed();
      } else {
        saved = await api.update(
          id: widget.user!.id,
          changes: _changes(),
          version: widget.user!.version,
        );
      }
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            _isNew
                ? 'Se registró a ${saved.fullName} y se le envió la invitación por correo.'
                : 'Se guardaron los datos.',
          ),
        ),
      );
      Navigator.of(context).pop(saved);
    } on ApiException catch (e) {
      if (!mounted) return;
      final fields = apiFieldErrors(e);
      setState(() {
        _error = apiErrorMessage(e);
        _fieldErrors = fields;
        final assignmentError = fields.entries
            .where((f) => f.key.startsWith('roleAssignments'))
            .map((f) => f.value)
            .firstOrNull;
        if (assignmentError != null) _assignmentsError = assignmentError;
      });
      _formKey.currentState!.validate();
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  /// Only the fields that changed (partial update).
  Map<String, Object?> _changes() {
    final user = widget.user!;
    final registration = _registration.text.trim();
    return {
      if (_firstNames.text.trim() != user.firstNames)
        'firstNames': _firstNames.text.trim(),
      if (_lastNames.text.trim() != user.lastNames)
        'lastNames': _lastNames.text.trim(),
      if (_jobTitle.text.trim() != user.jobTitle)
        'jobTitle': _jobTitle.text.trim(),
      if (registration != (user.professionalRegistration ?? ''))
        'professionalRegistration': registration.isEmpty ? null : registration,
    };
  }

  FormFieldValidator<String> _validator(
    String field,
    FormFieldValidator<String> local,
  ) =>
      (v) => local(v) ?? _fieldErrors[field];

  @override
  Widget build(BuildContext context) {
    final session = AppScope.of(context).session.session;
    return ModulePage(
      title: _isNew ? 'Invitar personal' : 'Editar datos',
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
          final clinicalRequired = _isNew
              ? _draft.requiresProfessionalRegistration(roles)
              : _hasClinicalRole(roles);
          const gap = SizedBox(height: KdConstants.space4);
          return ModuleListView(
            children: [
              if (_isNew)
                const Text(
                  'La persona recibirá un correo con un enlace de un solo uso para '
                  'activar su cuenta en esta clínica.',
                ),
              gap,
              ErrorMessage(_error),
              Form(
                key: _formKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    TextFormField(
                      controller: _firstNames,
                      textCapitalization: TextCapitalization.words,
                      textInputAction: TextInputAction.next,
                      validator: _validator(
                        'firstNames',
                        FormRules.requiredMax(120),
                      ),
                      decoration: fieldDecoration('Nombres'),
                    ),
                    gap,
                    TextFormField(
                      controller: _lastNames,
                      textCapitalization: TextCapitalization.words,
                      textInputAction: TextInputAction.next,
                      validator: _validator(
                        'lastNames',
                        FormRules.requiredMax(120),
                      ),
                      decoration: fieldDecoration('Apellidos'),
                    ),
                    gap,
                    if (_isNew) ...[
                      TextFormField(
                        controller: _email,
                        keyboardType: TextInputType.emailAddress,
                        autocorrect: false,
                        textInputAction: TextInputAction.next,
                        validator: _validator('email', FormRules.email),
                        decoration: fieldDecoration(
                          'Correo',
                          helper: 'Será su usuario para iniciar sesión.',
                        ),
                      ),
                      gap,
                    ] else ...[
                      InfoRow('Correo', widget.user!.email),
                      gap,
                    ],
                    TextFormField(
                      controller: _jobTitle,
                      textCapitalization: TextCapitalization.sentences,
                      textInputAction: TextInputAction.next,
                      validator: _validator(
                        'jobTitle',
                        FormRules.requiredMax(120),
                      ),
                      decoration: fieldDecoration(
                        'Descripción del puesto',
                        helper: 'Por ejemplo, Médico general o Recepción.',
                      ),
                    ),
                    gap,
                    TextFormField(
                      controller: _registration,
                      textInputAction: TextInputAction.done,
                      validator: _validator('professionalRegistration', (v) {
                        final t = v?.trim() ?? '';
                        if (clinicalRequired && t.isEmpty) {
                          return 'Es obligatorio para un rol clínico.';
                        }
                        if (t.length > 60) {
                          return 'Usa como máximo 60 caracteres.';
                        }
                        return null;
                      }),
                      decoration: fieldDecoration(
                        clinicalRequired
                            ? 'Registro profesional'
                            : 'Registro profesional (opcional)',
                        helper: 'Obligatorio solo para roles clínicos.',
                      ),
                    ),
                  ],
                ),
              ),
              if (_isNew) ...[
                gap,
                RoleAssignmentsEditor(
                  draft: _draft,
                  roles: roles,
                  branches: editorBranches(
                    session?.availableBranches ?? const [],
                  ),
                  callerIsOwner: session?.isOwner ?? false,
                  enabled: !_busy,
                  errorText: _assignmentsError,
                  onChanged: (d) => setState(() {
                    _draft = d;
                    _assignmentsError = null;
                  }),
                ),
              ],
              const SizedBox(height: KdConstants.space6),
              OnlineSubmitButton(
                label: _isNew ? 'Registrar e invitar' : 'Guardar cambios',
                busy: _busy,
                onPressed: () => _submit(roles),
              ),
            ],
          );
        },
      ),
    );
  }

  bool _hasClinicalRole(List<Role> roles) {
    final assigned = {for (final a in widget.user!.roleAssignments) a.role.id};
    return roles.any((r) => r.clinical && assigned.contains(r.id));
  }
}
