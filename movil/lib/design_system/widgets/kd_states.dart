import 'package:flutter/material.dart';

import '../theme.dart';
import '../tokens.g.dart';
import 'kd_button.dart';
import 'kd_icon.dart';
import 'kd_style.dart';

/// Empty, loading and error states share this layout: centered icon, title,
/// one sentence and at most one action, scrollable when the text is large.
class _StateLayout extends StatelessWidget {
  const _StateLayout({
    required this.visual,
    required this.title,
    this.message,
    this.action,
    this.liveRegion = false,
  });

  final Widget visual;
  final String title;
  final String? message;
  final Widget? action;
  final bool liveRegion;

  @override
  Widget build(BuildContext context) {
    final t = KdTokens.of(context);
    final text = message;
    final button = action;
    return Center(
      child: SingleChildScrollView(
        padding: EdgeInsets.all(t.space6),
        child: Semantics(
          liveRegion: liveRegion,
          container: true,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              visual,
              SizedBox(height: t.space4),
              Text(
                title,
                textAlign: TextAlign.center,
                style: t.typeSubtitle.copyWith(color: t.textDefault),
              ),
              if (text != null) ...[
                SizedBox(height: t.space2),
                Text(
                  text,
                  textAlign: TextAlign.center,
                  style: t.typeBody.copyWith(color: t.textMuted),
                ),
              ],
              if (button != null) ...[SizedBox(height: t.space6), button],
            ],
          ),
        ),
      ),
    );
  }
}

/// Nothing to show yet: [title] says what is missing («Aún no tienes
/// citas»), [message] what to do, and [action] (optional, the main action of
/// the view) does it.
class KdEmptyState extends StatelessWidget {
  const KdEmptyState({
    super.key,
    required this.title,
    this.message,
    this.icon = KdIcons.clipboardList,
    this.action,
  });

  final String title;
  final String? message;
  final KdIconData icon;
  final KdButton? action;

  @override
  Widget build(BuildContext context) {
    final t = KdTokens.of(context);
    return _StateLayout(
      visual: KdIcon(icon, size: KdStyle.stateIconSize, color: t.textMuted),
      title: title,
      message: message,
      action: action,
    );
  }
}

/// Content on its way. Announced to screen readers. With reduced motion the
/// spinner becomes a static clock icon, so nothing moves.
class KdLoadingState extends StatelessWidget {
  const KdLoadingState({super.key, this.title = 'Cargando…', this.message});

  final String title;
  final String? message;

  @override
  Widget build(BuildContext context) {
    final t = KdTokens.of(context);
    final visual = KdTheme.reduceMotion(context)
        ? KdIcon(
            KdIcons.clock,
            size: KdStyle.stateIconSize,
            color: t.colorAccent,
          )
        : SizedBox.square(
            dimension: KdStyle.stateIconSize,
            child: CircularProgressIndicator(
              color: t.colorAccent,
              backgroundColor: t.surfaceSunken,
              strokeWidth: t.space1,
            ),
          );
    return _StateLayout(
      visual: ExcludeSemantics(child: visual),
      title: title,
      message: message,
      liveRegion: true,
    );
  }
}

/// Something failed: [title] says what happened, [message] what to do
/// (DESIGN.md · Tono: no blame, no technical codes), and [onRetry] shows a
/// «Reintentar» action. Error icon plus text, never only color (R-08).
class KdErrorState extends StatelessWidget {
  const KdErrorState({
    super.key,
    this.title = 'No pudimos cargar la información',
    this.message = 'Revisa tu conexión e inténtalo de nuevo.',
    this.onRetry,
    this.retryLabel = 'Reintentar',
  });

  final String title;
  final String? message;
  final VoidCallback? onRetry;
  final String retryLabel;

  @override
  Widget build(BuildContext context) {
    final t = KdTokens.of(context);
    final retry = onRetry;
    return _StateLayout(
      visual: KdIcon(
        KdIcons.circleAlert,
        size: KdStyle.stateIconSize,
        color: t.colorError,
      ),
      title: title,
      message: message,
      liveRegion: true,
      action: retry == null
          ? null
          : KdButton.secondary(
              label: retryLabel,
              icon: KdIcons.refresh,
              onPressed: retry,
            ),
    );
  }
}
