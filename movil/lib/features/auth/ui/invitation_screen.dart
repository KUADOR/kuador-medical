import 'package:flutter/material.dart';

import '../../../app_scope.dart';
import '../../../core/api/api_exception.dart';
import '../../../core/connectivity/connectivity_widgets.dart';
import '../../../design_system/tokens.g.dart';
import '../../../design_system/widgets/widgets.dart';
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

  void _toLogin() => Navigator.of(context).popUntil((route) => route.isFirst);

  @override
  Widget build(BuildContext context) {
    const title = 'Activar tu cuenta';
    if (_loading) {
      return Scaffold(
        appBar: AppBar(title: const Text(title)),
        body: const SafeArea(
          child: KdLoadingState(title: 'Revisando la invitación'),
        ),
      );
    }
    final previewError = _previewError;
    final preview = _preview;
    if (preview == null) {
      final invalid = previewError?.code == 'ONE_TIME_TOKEN_INVALID';
      return AuthPage(
        title: title,
        // Retrying is the main action unless the link is no longer valid.
        primaryAction: invalid
            ? KdButton(
                label: 'Ir a iniciar sesión',
                expand: true,
                onPressed: _toLogin,
              )
            : OnlineSubmitButton(label: 'Reintentar', onPressed: _loadPreview),
        children: [
          KdInlineMessage.error(
            invalid
                ? 'La invitación expiró o ya se usó. Pide a la clínica que te la reenvíe.'
                : authErrorMessage(
                    previewError ?? const ApiException.unreachable(),
                  ),
          ),
          if (!invalid)
            Center(
              child: KdButton.text(
                label: 'Ir a iniciar sesión',
                onPressed: _toLogin,
              ),
            ),
        ],
      );
    }
    final t = KdTokens.of(context);
    return AuthPage(
      title: title,
      primaryAction: OnlineSubmitButton(
        label: 'Activar cuenta',
        busy: _busy,
        onPressed: _submit,
      ),
      children: [
        AuthHeader(
          title: 'Te invitaron a ${preview.clinicName}',
          message: 'Crea tu contraseña para entrar.',
        ),
        KdCard(
          child: Row(
            children: [
              KdIcon(KdIcons.mail, color: t.textMuted),
              SizedBox(width: t.space3),
              Expanded(
                child: Text(
                  'Cuenta: ${preview.maskedEmail}',
                  style: t.typeBody.copyWith(color: t.textDefault),
                ),
              ),
            ],
          ),
        ),
        if (_error != null) KdInlineMessage.error(_error!),
        Form(
          key: _formKey,
          child: AutofillGroup(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                KdTextField(
                  label: 'Contraseña',
                  controller: _password,
                  obscureText: true,
                  helperText: 'Usa al menos $minPasswordLength caracteres.',
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
