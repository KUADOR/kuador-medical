import 'package:flutter/material.dart';

import '../../../core/connectivity/connectivity_widgets.dart';

/// Minimum length of a new password: the api default (`PasswordProperties`).
/// The api is the authority and answers `WEAK_PASSWORD` if its policy differs.
const int minPasswordLength = 12;

/// Scrollable, centered page used by the auth screens (mobile-first, readable
/// width on tablets).
class AuthPage extends StatelessWidget {
  const AuthPage({
    super.key,
    required this.title,
    required this.children,
    this.showOfflineBanner = true,
  });

  final String title;
  final List<Widget> children;
  final bool showOfflineBanner;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(title)),
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 480),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  if (showOfflineBanner) const OfflineBanner(),
                  if (showOfflineBanner) const SizedBox(height: 16),
                  ...children,
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Error announced to screen readers; renders nothing when [message] is null.
class ErrorMessage extends StatelessWidget {
  const ErrorMessage(this.message, {super.key});

  final String? message;

  @override
  Widget build(BuildContext context) {
    final text = message;
    if (text == null) return const SizedBox.shrink();
    final scheme = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: Semantics(
        liveRegion: true,
        container: true,
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(Icons.error_outline, color: scheme.error),
            const SizedBox(width: 8),
            Expanded(
              child: Text(
                text,
                style: TextStyle(
                  color: scheme.error,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Confirmation announced to screen readers.
class SuccessMessage extends StatelessWidget {
  const SuccessMessage(this.message, {super.key});

  final String message;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Semantics(
      liveRegion: true,
      container: true,
      child: Column(
        children: [
          Icon(
            Icons.check_circle_outline,
            size: 48,
            color: theme.colorScheme.primary,
          ),
          const SizedBox(height: 16),
          Text(
            message,
            textAlign: TextAlign.center,
            style: theme.textTheme.titleMedium,
          ),
        ],
      ),
    );
  }
}

/// Password input with a show/hide toggle.
class PasswordField extends StatefulWidget {
  const PasswordField({
    super.key,
    required this.controller,
    required this.label,
    this.validator,
    this.autofillHints = const [AutofillHints.password],
    this.textInputAction = TextInputAction.done,
    this.onSubmitted,
    this.helperText,
  });

  final TextEditingController controller;
  final String label;
  final FormFieldValidator<String>? validator;
  final Iterable<String> autofillHints;
  final TextInputAction textInputAction;
  final ValueChanged<String>? onSubmitted;
  final String? helperText;

  @override
  State<PasswordField> createState() => _PasswordFieldState();
}

class _PasswordFieldState extends State<PasswordField> {
  bool _obscured = true;

  @override
  Widget build(BuildContext context) {
    return TextFormField(
      controller: widget.controller,
      obscureText: _obscured,
      enableSuggestions: false,
      autocorrect: false,
      autofillHints: widget.autofillHints,
      textInputAction: widget.textInputAction,
      onFieldSubmitted: widget.onSubmitted,
      validator: widget.validator,
      decoration: InputDecoration(
        labelText: widget.label,
        helperText: widget.helperText,
        helperMaxLines: 2,
        border: const OutlineInputBorder(),
        suffixIcon: IconButton(
          tooltip: _obscured ? 'Mostrar contraseña' : 'Ocultar contraseña',
          icon: Icon(_obscured ? Icons.visibility : Icons.visibility_off),
          onPressed: () => setState(() => _obscured = !_obscured),
        ),
      ),
    );
  }
}

String? requiredText(String? value) => (value == null || value.trim().isEmpty)
    ? 'Este dato es obligatorio.'
    : null;

String? emailValidator(String? value) {
  final v = value?.trim() ?? '';
  if (v.isEmpty) return 'Ingresa tu correo.';
  if (v.length > 254 || !RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$').hasMatch(v)) {
    return 'Ingresa un correo válido.';
  }
  return null;
}

String? newPasswordValidator(String? value) {
  final v = value ?? '';
  if (v.isEmpty) return 'Ingresa una contraseña.';
  if (v.runes.length < minPasswordLength) {
    return 'Usa al menos $minPasswordLength caracteres.';
  }
  if (v.length > 128) return 'Usa como máximo 128 caracteres.';
  return null;
}
