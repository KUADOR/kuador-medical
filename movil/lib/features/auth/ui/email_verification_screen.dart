import 'package:flutter/material.dart';

import '../../../app_scope.dart';
import '../../../core/api/api_exception.dart';
import '../../../core/connectivity/connectivity_widgets.dart';
import '../../../design_system/widgets/widgets.dart';
import 'auth_messages.dart';
import 'auth_widgets.dart';

/// Confirms the email of a self-registered patient (opened by deep link).
class EmailVerificationScreen extends StatefulWidget {
  const EmailVerificationScreen({super.key, required this.token});

  final String token;

  @override
  State<EmailVerificationScreen> createState() =>
      _EmailVerificationScreenState();
}

class _EmailVerificationScreenState extends State<EmailVerificationScreen> {
  bool _busy = false;
  bool _done = false;
  ApiException? _error;

  Future<void> _confirm() async {
    if (_busy) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await AppScope.of(context).authApi.confirmEmailVerification(widget.token);
      if (mounted) setState(() => _done = true);
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _toLogin() => Navigator.of(context).popUntil((route) => route.isFirst);

  @override
  Widget build(BuildContext context) {
    const title = 'Verificar correo';
    if (_done) {
      return AuthPage(
        title: title,
        showOfflineBanner: false,
        primaryAction: KdButton(
          label: 'Ir a iniciar sesión',
          expand: true,
          onPressed: _toLogin,
        ),
        children: const [
          SuccessMessage(
            'Tu correo quedó verificado. Ya puedes iniciar sesión.',
            title: 'Correo verificado',
          ),
        ],
      );
    }
    final error = _error;
    final invalid = error?.code == 'ONE_TIME_TOKEN_INVALID';
    if (invalid) {
      return AuthPage(
        title: title,
        primaryAction: KdButton(
          label: 'Ir a iniciar sesión',
          expand: true,
          onPressed: _toLogin,
        ),
        children: const [
          KdInlineMessage.error(
            'El enlace de verificación expiró o ya se usó.',
          ),
        ],
      );
    }
    return AuthPage(
      title: title,
      primaryAction: OnlineSubmitButton(
        label: 'Confirmar mi correo',
        busy: _busy,
        onPressed: _confirm,
      ),
      children: [
        const AuthHeader(
          title: 'Confirma tu correo',
          message: 'Así activamos tu cuenta de paciente.',
        ),
        if (error != null) KdInlineMessage.error(authErrorMessage(error)),
        Center(
          child: KdButton.text(
            label: 'Ir a iniciar sesión',
            onPressed: _toLogin,
          ),
        ),
      ],
    );
  }
}
