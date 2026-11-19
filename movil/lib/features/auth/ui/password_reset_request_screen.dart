import 'package:flutter/material.dart';

import '../../../app_scope.dart';
import '../../../core/api/api_exception.dart';
import '../../../core/connectivity/connectivity_widgets.dart';
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
    if (_sent) {
      return AuthPage(
        title: 'Recuperar acceso',
        showOfflineBanner: false,
        children: [
          const SuccessMessage(
            'Si el correo está registrado, te enviamos un enlace para cada clínica donde tienes cuenta. '
            'Ábrelo en este teléfono; vence pronto y solo se puede usar una vez.',
          ),
          const SizedBox(height: 24),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Volver a iniciar sesión'),
          ),
        ],
      );
    }
    return AuthPage(
      title: 'Recuperar acceso',
      children: [
        const Text(
          'Escribe el correo con el que inicias sesión. Te enviaremos un enlace para crear una nueva contraseña.',
        ),
        const SizedBox(height: 16),
        ErrorMessage(_error),
        Form(
          key: _formKey,
          child: TextFormField(
            controller: _email,
            keyboardType: TextInputType.emailAddress,
            autocorrect: false,
            autofillHints: const [AutofillHints.email],
            validator: emailValidator,
            decoration: const InputDecoration(
              labelText: 'Correo',
              border: OutlineInputBorder(),
            ),
          ),
        ),
        const SizedBox(height: 24),
        OnlineSubmitButton(
          label: 'Enviar enlace',
          busy: _busy,
          onPressed: _submit,
        ),
      ],
    );
  }
}
