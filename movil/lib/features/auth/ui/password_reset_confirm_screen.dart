import 'package:flutter/material.dart';

import '../../../app_scope.dart';
import '../../../core/api/api_exception.dart';
import '../../../core/connectivity/connectivity_widgets.dart';
import '../../../design_system/tokens.g.dart';
import '../../../design_system/widgets/widgets.dart';
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
    const title = 'Nueva contraseña';
    final toLogin = KdButton(
      label: _done ? 'Ir a iniciar sesión' : 'Volver a iniciar sesión',
      expand: true,
      onPressed: _toLogin,
    );
    if (_done) {
      return AuthPage(
        title: title,
        showOfflineBanner: false,
        primaryAction: toLogin,
        children: const [
          SuccessMessage(
            'Tu contraseña se actualizó. Inicia sesión con la nueva contraseña.',
            title: 'Contraseña actualizada',
          ),
        ],
      );
    }
    if (_linkInvalid) {
      return AuthPage(
        title: title,
        primaryAction: toLogin,
        children: [if (_error != null) KdInlineMessage.error(_error!)],
      );
    }
    final t = KdTokens.of(context);
    return AuthPage(
      title: title,
      primaryAction: OnlineSubmitButton(
        label: 'Guardar contraseña',
        busy: _busy,
        onPressed: _submit,
      ),
      children: [
        const AuthHeader(
          title: 'Crea una contraseña nueva',
          message: 'La usarás para entrar desde ahora.',
        ),
        if (_error != null) KdInlineMessage.error(_error!),
        Form(
          key: _formKey,
          child: AutofillGroup(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                KdTextField(
                  label: 'Nueva contraseña',
                  controller: _password,
                  obscureText: true,
                  helperText:
                      'Usa al menos $minPasswordLength caracteres, distinta de la anterior.',
                  autofillHints: const [AutofillHints.newPassword],
                  validator: newPasswordValidator,
                ),
                SizedBox(height: t.space4),
                KdTextField(
                  label: 'Repite la contraseña',
                  controller: _confirm,
                  obscureText: true,
                  autofillHints: const [AutofillHints.newPassword],
                  textInputAction: TextInputAction.done,
                  validator: (v) => v != _password.text
                      ? 'Las contraseñas no coinciden.'
                      : null,
                  onSubmitted: (_) {
                    if (ConnectivityScope.of(context).isOnline) _submit();
                  },
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}
