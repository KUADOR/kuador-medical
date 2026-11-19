import 'package:flutter/material.dart';

import '../../../app_scope.dart';
import '../../../core/api/api_exception.dart';
import '../../../core/connectivity/connectivity_widgets.dart';
import '../data/auth_models.dart';
import 'auth_messages.dart';
import 'auth_widgets.dart';
import 'login_flow.dart';

/// Accepts an invitation (staff, or an adult patient) opened by deep link:
/// shows the clinic and the masked email, then sets the password.
class InvitationScreen extends StatefulWidget {
  const InvitationScreen({super.key, required this.token});

  final String token;

  @override
  State<InvitationScreen> createState() => _InvitationScreenState();
}

class _InvitationScreenState extends State<InvitationScreen> {
  final _formKey = GlobalKey<FormState>();
  final _password = TextEditingController();
  final _confirm = TextEditingController();
  InvitationPreview? _preview;
  ApiException? _previewError;
  bool _loading = true;
  bool _busy = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _loadPreview();
    });
  }

  @override
  void dispose() {
    _password.dispose();
    _confirm.dispose();
    super.dispose();
  }

  Future<void> _loadPreview() async {
    setState(() {
      _loading = true;
      _previewError = null;
    });
    try {
      final preview = await AppScope.of(
        context,
      ).authApi.previewInvitation(widget.token);
      if (mounted) setState(() => _preview = preview);
    } on ApiException catch (e) {
      if (mounted) setState(() => _previewError = e);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _submit() async {
    if (_busy || !_formKey.currentState!.validate()) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final result = await AppScope.of(
        context,
      ).authApi.acceptInvitation(token: widget.token, password: _password.text);
      if (!mounted) return;
      await continueLogin(context, result);
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        if (e.code == 'ONE_TIME_TOKEN_INVALID') {
          _preview = null;
          _previewError = e;
        } else {
          _error = authErrorMessage(e);
        }
      });
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    const title = 'Activar tu cuenta';
    if (_loading) {
      return AuthPage(
        title: title,
        children: [
          Semantics(
            liveRegion: true,
            label: 'Revisando la invitación',
            child: const Center(child: CircularProgressIndicator()),
          ),
        ],
      );
    }
    final previewError = _previewError;
    final preview = _preview;
    if (preview == null) {
      final invalid = previewError?.code == 'ONE_TIME_TOKEN_INVALID';
      return AuthPage(
        title: title,
        children: [
          ErrorMessage(
            invalid
                ? 'La invitación expiró o ya se usó. Pide a la clínica que te la reenvíe.'
                : authErrorMessage(
                    previewError ?? const ApiException.unreachable(),
                  ),
          ),
          if (!invalid)
            OnlineSubmitButton(label: 'Reintentar', onPressed: _loadPreview),
          TextButton(
            onPressed: () =>
                Navigator.of(context).popUntil((route) => route.isFirst),
            child: const Text('Ir a iniciar sesión'),
          ),
        ],
      );
    }
    return AuthPage(
      title: title,
      children: [
        Semantics(
          header: true,
          child: Text(
            'Te invitaron a ${preview.clinicName}',
            style: Theme.of(context).textTheme.titleLarge,
          ),
        ),
        const SizedBox(height: 8),
        Text('Cuenta: ${preview.maskedEmail}'),
        const SizedBox(height: 4),
        const Text('Crea tu contraseña para entrar.'),
        const SizedBox(height: 16),
        ErrorMessage(_error),
        Form(
          key: _formKey,
          child: AutofillGroup(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                PasswordField(
                  controller: _password,
                  label: 'Contraseña',
                  helperText: 'Usa al menos $minPasswordLength caracteres.',
                  autofillHints: const [AutofillHints.newPassword],
                  textInputAction: TextInputAction.next,
                  validator: newPasswordValidator,
                ),
                const SizedBox(height: 16),
                PasswordField(
                  controller: _confirm,
                  label: 'Repite la contraseña',
                  autofillHints: const [AutofillHints.newPassword],
                  validator: (v) => v != _password.text
                      ? 'Las contraseñas no coinciden.'
                      : null,
                ),
                const SizedBox(height: 24),
                OnlineSubmitButton(
                  label: 'Activar cuenta',
                  busy: _busy,
                  onPressed: _submit,
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}
