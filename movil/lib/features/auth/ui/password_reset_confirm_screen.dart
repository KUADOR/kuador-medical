import 'package:flutter/material.dart';

import '../../../app_scope.dart';
import '../../../core/api/api_exception.dart';
import '../../../core/connectivity/connectivity_widgets.dart';
import 'auth_messages.dart';
import 'auth_widgets.dart';

/// Sets a new password with the token of a reset link (opened by deep link).
class PasswordResetConfirmScreen extends StatefulWidget {
  const PasswordResetConfirmScreen({super.key, required this.token});

  final String token;

  @override
  State<PasswordResetConfirmScreen> createState() =>
      _PasswordResetConfirmScreenState();
}

class _PasswordResetConfirmScreenState
    extends State<PasswordResetConfirmScreen> {
  final _formKey = GlobalKey<FormState>();
  final _password = TextEditingController();
  final _confirm = TextEditingController();
  bool _busy = false;
  bool _done = false;
  bool _linkInvalid = false;
  String? _error;

  @override
  void dispose() {
    _password.dispose();
    _confirm.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_busy || !_formKey.currentState!.validate()) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await AppScope.of(context).authApi.confirmPasswordReset(
        token: widget.token,
        newPassword: _password.text,
      );
      if (mounted) setState(() => _done = true);
    } on ApiException catch (e) {
      if (mounted) {
        setState(() {
          _error = authErrorMessage(e);
          _linkInvalid = e.code == 'ONE_TIME_TOKEN_INVALID';
        });
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _toLogin() => Navigator.of(context).popUntil((route) => route.isFirst);

  @override
  Widget build(BuildContext context) {
    if (_done) {
      return AuthPage(
        title: 'Nueva contraseña',
        showOfflineBanner: false,
        children: [
          const SuccessMessage(
            'Tu contraseña se actualizó. Inicia sesión con la nueva contraseña.',
          ),
          const SizedBox(height: 24),
          FilledButton(
            onPressed: _toLogin,
            child: const Text('Ir a iniciar sesión'),
          ),
        ],
      );
    }
    return AuthPage(
      title: 'Nueva contraseña',
      children: [
        ErrorMessage(_error),
        if (_linkInvalid)
          FilledButton(
            onPressed: _toLogin,
            child: const Text('Volver a iniciar sesión'),
          )
        else
          Form(
            key: _formKey,
            child: AutofillGroup(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  PasswordField(
                    controller: _password,
                    label: 'Nueva contraseña',
                    helperText:
                        'Usa al menos $minPasswordLength caracteres, distinta de la anterior.',
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
                    label: 'Guardar contraseña',
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
