import 'package:flutter/material.dart';

import '../../../app_scope.dart';
import '../../../core/api/api_exception.dart';
import '../../../core/connectivity/connectivity_widgets.dart';
import '../../../design_system/widgets/widgets.dart';
import 'auth_messages.dart';
import 'auth_widgets.dart';

/// Asks for a password reset link (FR-005). The answer never reveals whether
/// the email exists.
class PasswordResetRequestScreen extends StatefulWidget {
  const PasswordResetRequestScreen({super.key});

  @override
  State<PasswordResetRequestScreen> createState() =>
      _PasswordResetRequestScreenState();
}

class _PasswordResetRequestScreenState
    extends State<PasswordResetRequestScreen> {
  final _formKey = GlobalKey<FormState>();
  final _email = TextEditingController();
  bool _busy = false;
  bool _sent = false;
  String? _error;

  @override
  void dispose() {
    _email.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_busy || !_formKey.currentState!.validate()) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await AppScope.of(context).authApi.requestPasswordReset(_email.text);
      if (mounted) setState(() => _sent = true);
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = authErrorMessage(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    const title = 'Recuperar acceso';
    if (_sent) {
      return AuthPage(
        title: title,
        showOfflineBanner: false,
        primaryAction: KdButton(
          label: 'Volver a iniciar sesión',
          expand: true,
          onPressed: () => Navigator.of(context).pop(),
        ),
        children: const [
          SuccessMessage(
            'Si el correo está registrado, te enviamos un enlace para cada clínica donde tienes cuenta. '
            'Ábrelo en este teléfono; vence pronto y solo se puede usar una vez.',
            title: 'Revisa tu correo',
          ),
        ],
      );
    }
    return AuthPage(
      title: title,
      primaryAction: OnlineSubmitButton(
        label: 'Enviar enlace',
        busy: _busy,
        onPressed: _submit,
      ),
      children: [
        const AuthHeader(
          title: '¿Olvidaste tu contraseña?',
          message:
              'Escribe el correo con el que inicias sesión. Te enviaremos un enlace para crear una nueva contraseña.',
        ),
        if (_error != null) KdInlineMessage.error(_error!),
        Form(
          key: _formKey,
          child: KdTextField(
            label: 'Correo',
            controller: _email,
            prefixIcon: KdIcons.mail,
            keyboardType: TextInputType.emailAddress,
            autofillHints: const [AutofillHints.email],
            textInputAction: TextInputAction.done,
            validator: emailValidator,
            onSubmitted: (_) {
              if (ConnectivityScope.of(context).isOnline) _submit();
            },
          ),
        ),
      ],
    );
  }
}
