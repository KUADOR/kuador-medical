import 'package:flutter/material.dart';

import '../../../core/connectivity/connectivity_widgets.dart';
import '../../../design_system/layout.dart';
import '../../../design_system/tokens.g.dart';
import '../../../design_system/widgets/widgets.dart';

/// Minimum length of a new password: the api default (`PasswordProperties`).
/// The api is the authority and answers `WEAK_PASSWORD` if its policy differs.
const int minPasswordLength = 12;

/// Page of the sign-in and sign-up screens (MEDK-45), mobile-first:
///
/// - the content scrolls, never stays under the keyboard and dragging it
///   hides the keyboard ([KdFormScrollView]);
/// - the [primaryAction] (one per screen, DESIGN.md R-02) is pinned at the
///   bottom, within reach of the thumb and right above the keyboard;
/// - the offline notice comes first while there is no connection;
/// - on tablets the content keeps a readable width.
///
/// The app bar keeps the native back gesture and button of each platform.
class AuthPage extends StatelessWidget {
  const AuthPage({
    super.key,
    required this.title,
    required this.children,
    this.primaryAction,
    this.showOfflineBanner = true,
  });

  final String title;
  final List<Widget> children;

  /// The main action of the screen, usually a full-width [KdButton] or an
  /// [OnlineSubmitButton].
  final Widget? primaryAction;
  final bool showOfflineBanner;

  @override
  Widget build(BuildContext context) {
    final t = KdTokens.of(context);
    final offline =
        showOfflineBanner && !ConnectivityScope.of(context).isOnline;
    final action = primaryAction;
    return Scaffold(
      appBar: AppBar(title: Text(title)),
      body: SafeArea(
        child: Column(
          children: [
            Expanded(
              child: _ReadableWidth(
                child: KdFormScrollView(
                  children: [if (offline) const OfflineBanner(), ...children],
                ),
              ),
            ),
            if (action != null)
              DecoratedBox(
                decoration: BoxDecoration(
                  color: t.surfaceBase,
                  border: Border(top: t.borderHairline),
                ),
                child: Padding(
                  padding: EdgeInsets.fromLTRB(
                    t.space4,
                    t.space3,
                    t.space4,
                    t.space4,
                  ),
                  child: _ReadableWidth(child: action),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _ReadableWidth extends StatelessWidget {
  const _ReadableWidth({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) => Align(
    alignment: Alignment.topCenter,
    child: ConstrainedBox(
      constraints: const BoxConstraints(maxWidth: KdLayout.formMaxWidth),
      child: child,
    ),
  );
}

/// Heading of an auth screen: a [title] announced as a header and, at most,
/// one sentence under it (DESIGN.md · Tono).
class AuthHeader extends StatelessWidget {
  const AuthHeader({
    super.key,
    required this.title,
    this.message,
    this.display = false,
  });

  final String title;
  final String? message;

  /// Larger title, for the first screen of the app.
  final bool display;

  @override
  Widget build(BuildContext context) {
    final t = KdTokens.of(context);
    final text = message;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Semantics(
          header: true,
          child: Text(
            title,
            style: (display ? t.typeDisplay : t.typeTitle).copyWith(
              color: t.textDefault,
            ),
          ),
        ),
        if (text != null) ...[
          SizedBox(height: t.space2),
          Text(text, style: t.typeBody.copyWith(color: t.textMuted)),
        ],
      ],
    );
  }
}

/// Error announced to screen readers, with space below; renders nothing when
/// [message] is null. Used by forms that stack their own fields.
class ErrorMessage extends StatelessWidget {
  const ErrorMessage(this.message, {super.key});

  final String? message;

  @override
  Widget build(BuildContext context) {
    final text = message;
    if (text == null) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.only(bottom: KdConstants.space4),
      child: KdInlineMessage.error(text),
    );
  }
}

/// Confirmation that ends a flow (a sent link, a verified email), announced
/// to screen readers.
class SuccessMessage extends StatelessWidget {
  const SuccessMessage(this.message, {super.key, this.title});

  final String message;
  final String? title;

  @override
  Widget build(BuildContext context) {
    final t = KdTokens.of(context);
    final heading = title;
    return Semantics(
      liveRegion: true,
      container: true,
      child: Column(
        children: [
          SizedBox(height: t.space8),
          KdIcon(
            KdIcons.circleCheck,
            size: KdLayout.statusIconSize,
            color: t.colorOk,
          ),
          SizedBox(height: t.space4),
          if (heading != null) ...[
            Semantics(
              header: true,
              child: Text(
                heading,
                textAlign: TextAlign.center,
                style: t.typeTitle.copyWith(color: t.textDefault),
              ),
            ),
            SizedBox(height: t.space2),
          ],
          Text(
            message,
            textAlign: TextAlign.center,
            style: t.typeBody.copyWith(color: t.textMuted),
          ),
        ],
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
