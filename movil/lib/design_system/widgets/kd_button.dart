import 'package:flutter/material.dart';

import '../theme.dart';
import '../tokens.g.dart';
import 'kd_icon.dart';
import 'kd_style.dart';

/// Visual weight of a [KdButton] (DESIGN.md, R-02).
enum KdButtonVariant {
  /// Filled with the accent. One per screen, sheet or dialog.
  primary,

  /// Outlined. Any other action.
  secondary,

  /// Text only, accent colored. Low-emphasis actions.
  text,

  /// Filled with color.error. Destructive confirmations only.
  destructive,
}

/// Button of the design system: token colors, radius `md`, at least
/// `size.target-min` tall (44, and a 48 tap area on Android), visible focus
/// ring and Spanish labels with a verb and an object («Confirmar cita»).
///
/// While [loading] is true it is disabled and shows a progress indicator
/// next to [label] (a static clock with reduced motion).
class KdButton extends StatelessWidget {
  const KdButton({
    super.key,
    required this.label,
    required this.onPressed,
    this.variant = KdButtonVariant.primary,
    this.icon,
    this.loading = false,
    this.expand = false,
  });

  const KdButton.secondary({
    super.key,
    required this.label,
    required this.onPressed,
    this.icon,
    this.loading = false,
    this.expand = false,
  }) : variant = KdButtonVariant.secondary;

  const KdButton.text({
    super.key,
    required this.label,
    required this.onPressed,
    this.icon,
    this.loading = false,
    this.expand = false,
  }) : variant = KdButtonVariant.text;

  const KdButton.destructive({
    super.key,
    required this.label,
    required this.onPressed,
    this.icon,
    this.loading = false,
    this.expand = false,
  }) : variant = KdButtonVariant.destructive;

  final String label;

  /// Null disables the button.
  final VoidCallback? onPressed;
  final KdButtonVariant variant;
  final KdIconData? icon;
  final bool loading;

  /// Takes the full available width (main action of a patient screen).
  final bool expand;

  @override
  Widget build(BuildContext context) {
    final t = KdTokens.of(context);
    final style = _style(context, t);
    final callback = loading ? null : onPressed;
    final child = _content(t, reduceMotion: KdTheme.reduceMotion(context));

    final button = switch (variant) {
      KdButtonVariant.primary || KdButtonVariant.destructive => FilledButton(
        onPressed: callback,
        style: style,
        child: child,
      ),
      KdButtonVariant.secondary => OutlinedButton(
        onPressed: callback,
        style: style,
        child: child,
      ),
      KdButtonVariant.text => TextButton(
        onPressed: callback,
        style: style,
        child: child,
      ),
    };
    return expand ? SizedBox(width: double.infinity, child: button) : button;
  }

  Widget _content(KdTokens t, {required bool reduceMotion}) {
    final text = Text(label, textAlign: TextAlign.center);
    if (loading) {
      // With reduced motion a static clock replaces the spinner.
      final indicator = reduceMotion
          ? KdIcon(KdIcons.clock, size: KdStyle.inlineIconSize)
          : SizedBox.square(
              dimension: t.space4,
              child: const CircularProgressIndicator(
                strokeWidth: KdStyle.focusRingWidth,
              ),
            );
      return Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          ExcludeSemantics(child: indicator),
          SizedBox(width: t.space2),
          Flexible(child: text),
        ],
      );
    }
    final leading = icon;
    if (leading == null) return text;
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        KdIcon(leading, size: KdStyle.inlineIconSize),
        SizedBox(width: t.space2),
        Flexible(child: text),
      ],
    );
  }

  ButtonStyle _style(BuildContext context, KdTokens t) {
    final (Color fill, Color onFill) = switch (variant) {
      KdButtonVariant.destructive => (t.colorError, t.colorOnError),
      _ => (t.colorAccent, t.colorOnAccent),
    };
    final filled =
        variant == KdButtonVariant.primary ||
        variant == KdButtonVariant.destructive;
    final disabledFg = t.textMuted;

    return ButtonStyle(
      minimumSize: WidgetStatePropertyAll(
        Size(t.sizeTargetMin, t.sizeTargetMin),
      ),
      padding: WidgetStatePropertyAll(
        EdgeInsets.symmetric(horizontal: t.space4, vertical: t.space2),
      ),
      tapTargetSize: MaterialTapTargetSize.padded,
      textStyle: WidgetStatePropertyAll(t.typeLabel),
      animationDuration: KdStyle.motion(context, t.motionDurationFast),
      elevation: const WidgetStatePropertyAll(0),
      shape: WidgetStatePropertyAll(
        RoundedRectangleBorder(borderRadius: BorderRadius.circular(t.radiusMd)),
      ),
      backgroundColor: WidgetStateProperty.resolveWith((states) {
        if (!filled) return null;
        if (states.contains(WidgetState.disabled)) return t.surfaceSunken;
        return fill;
      }),
      foregroundColor: WidgetStateProperty.resolveWith((states) {
        if (states.contains(WidgetState.disabled)) return disabledFg;
        return filled ? onFill : fill;
      }),
      iconColor: WidgetStateProperty.resolveWith((states) {
        if (states.contains(WidgetState.disabled)) return disabledFg;
        return filled ? onFill : fill;
      }),
      overlayColor: WidgetStateProperty.resolveWith((states) {
        final base = filled ? onFill : fill;
        if (states.contains(WidgetState.pressed)) {
          return base.withValues(alpha: KdStyle.selectionTintOpacity);
        }
        if (states.contains(WidgetState.hovered) ||
            states.contains(WidgetState.focused)) {
          return base.withValues(alpha: KdStyle.selectionTintOpacity / 2);
        }
        return null;
      }),
      side: WidgetStateProperty.resolveWith((states) {
        if (states.contains(WidgetState.focused)) return KdStyle.focusRing(t);
        if (variant != KdButtonVariant.secondary) return null;
        if (states.contains(WidgetState.disabled)) return t.borderHairline;
        return t.borderStrong;
      }),
    );
  }
}
