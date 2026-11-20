import 'package:flutter/material.dart';

import '../../../app_scope.dart';
import '../../../core/api/api_exception.dart';
import '../../../core/connectivity/connectivity_widgets.dart';
import '../../../core/session/session_controller.dart';
import '../../../design_system/tokens.g.dart';
import '../../../design_system/widgets/widgets.dart';
import '../../../system_status/system_status_screen.dart';
import 'auth_messages.dart';
import 'auth_widgets.dart';
import 'login_flow.dart';
import 'password_reset_request_screen.dart';
import 'self_registration_screen.dart';

/// Email and password login (FR-004). Errors are generic (FR-006).
///
/// One main action, «Ingresar», pinned above the keyboard; «next» on the
/// email moves to the password and «done» there signs in (MEDK-45).
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
    final t = KdTokens.of(context);
    final services = AppScope.of(context);
    final expired =
        services.session.signOutReason == SignOutReason.expired &&
        _error == null;
    final error = expired
        ? 'Tu sesión terminó. Inicia sesión de nuevo.'
        : _error;
    return AuthPage(
      title: 'Iniciar sesión',
      primaryAction: OnlineSubmitButton(
        label: 'Ingresar',
        busy: _busy,
        onPressed: _submit,
      ),
      children: [
        const AuthHeader(
          title: 'Medical Kuador',
          message: 'Ingresa con el correo y la contraseña de tu clínica.',
          display: true,
        ),
        if (error != null) KdInlineMessage.error(error),
        Form(
          key: _formKey,
          child: AutofillGroup(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                KdTextField(
                  label: 'Correo',
                  controller: _email,
                  prefixIcon: KdIcons.mail,
                  keyboardType: TextInputType.emailAddress,
                  autofillHints: const [
                    AutofillHints.username,
                    AutofillHints.email,
                  ],
                  validator: emailValidator,
                ),
                SizedBox(height: t.space4),
                KdTextField(
                  label: 'Contraseña',
                  controller: _password,
                  prefixIcon: KdIcons.lock,
                  obscureText: true,
                  autofillHints: const [AutofillHints.password],
                  textInputAction: TextInputAction.done,
                  validator: (v) => (v == null || v.isEmpty)
                      ? 'Ingresa tu contraseña.'
                      : null,
                  onSubmitted: (_) {
                    if (ConnectivityScope.of(context).isOnline) _submit();
                  },
                ),
              ],
            ),
          ),
        ),
        Align(
          alignment: AlignmentDirectional.centerStart,
          child: KdButton.text(
            label: '¿Olvidaste tu contraseña?',
            onPressed: () => _open(const PasswordResetRequestScreen()),
          ),
        ),
        KdButton.secondary(
          label: 'Soy paciente y quiero registrarme',
          icon: KdIcons.userPlus,
          expand: true,
          onPressed: () => _open(const SelfRegistrationScreen()),
        ),
        Center(
          child: KdButton.text(
            label: 'Estado del sistema',
            onPressed: () =>
                _open(SystemStatusScreen(client: services.systemInfoClient)),
          ),
        ),
      ],
    );
  }
}
