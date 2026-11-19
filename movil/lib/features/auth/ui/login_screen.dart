import 'package:flutter/material.dart';

import '../../../app_scope.dart';
import '../../../core/api/api_exception.dart';
import '../../../core/connectivity/connectivity_widgets.dart';
import '../../../core/session/session_controller.dart';
import '../../../system_status/system_status_screen.dart';
import 'auth_messages.dart';
import 'auth_widgets.dart';
import 'login_flow.dart';
import 'password_reset_request_screen.dart';
import 'self_registration_screen.dart';

/// Email and password login (FR-004). Errors are generic (FR-006).
class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _email = TextEditingController();
  final _password = TextEditingController();
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    super.dispose();
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
      ).authApi.login(email: _email.text, password: _password.text);
      if (!mounted) return;
      _password.clear();
      await continueLogin(context, result);
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = authErrorMessage(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _open(Widget screen) {
    Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => screen));
  }

  @override
  Widget build(BuildContext context) {
    final services = AppScope.of(context);
    final expired =
        services.session.signOutReason == SignOutReason.expired &&
        _error == null;
    return AuthPage(
      title: 'Iniciar sesión',
      children: [
        Semantics(
          header: true,
          child: Text(
            'Medical Kuador',
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.headlineSmall,
          ),
        ),
        const SizedBox(height: 24),
        ErrorMessage(
          expired ? 'Tu sesión terminó. Inicia sesión de nuevo.' : _error,
        ),
        Form(
          key: _formKey,
          child: AutofillGroup(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                TextFormField(
                  controller: _email,
                  keyboardType: TextInputType.emailAddress,
                  autocorrect: false,
                  autofillHints: const [
                    AutofillHints.username,
                    AutofillHints.email,
                  ],
                  textInputAction: TextInputAction.next,
                  validator: emailValidator,
                  decoration: const InputDecoration(
                    labelText: 'Correo',
                    border: OutlineInputBorder(),
                  ),
                ),
                const SizedBox(height: 16),
                PasswordField(
                  controller: _password,
                  label: 'Contraseña',
                  validator: (v) => (v == null || v.isEmpty)
                      ? 'Ingresa tu contraseña.'
                      : null,
                  onSubmitted: (_) {
                    if (ConnectivityScope.of(context).isOnline) _submit();
                  },
                ),
                const SizedBox(height: 24),
                OnlineSubmitButton(
                  label: 'Ingresar',
                  busy: _busy,
                  onPressed: _submit,
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 8),
        TextButton(
          onPressed: () => _open(const PasswordResetRequestScreen()),
          child: const Text('¿Olvidaste tu contraseña?'),
        ),
        TextButton(
          onPressed: () => _open(const SelfRegistrationScreen()),
          child: const Text('Soy paciente y quiero registrarme'),
        ),
        TextButton(
          onPressed: () =>
              _open(SystemStatusScreen(client: services.systemInfoClient)),
          child: const Text('Estado del sistema'),
        ),
      ],
    );
  }
}
