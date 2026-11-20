import 'package:flutter/material.dart';

import '../tokens.g.dart';
import 'kd_style.dart';

/// Card of the design system: surface.raised, radius `lg`, 1 px hairline
/// border and no shadow (DESIGN.md, R-03 and R-04).
///
/// With [onTap] the whole card is one button for screen readers, with
/// [semanticLabel] (or its texts) as name, and shows the focus ring. With
/// [selected] it gets the soft selection background and an accent border
/// (R-10).
class KdCard extends StatelessWidget {
  const KdCard({
    super.key,
    required this.child,
    this.onTap,
    this.selected = false,
    this.semanticLabel,
    this.padding,
  });

  final Widget child;
  final VoidCallback? onTap;
  final bool selected;
  final String? semanticLabel;

  /// Defaults to space.4 on every side.
  final EdgeInsetsGeometry? padding;

  @override
  Widget build(BuildContext context) {
    final t = KdTokens.of(context);
    final radius = BorderRadius.circular(t.radiusLg);
    final background = selected
        ? KdStyle.selectionTint(t, t.surfaceRaised)
        : t.surfaceRaised;
    final border = selected
        ? BorderSide(color: t.colorAccent, width: KdStyle.focusRingWidth)
        : t.borderHairline;
    final content = Padding(
      padding: padding ?? EdgeInsets.all(t.space4),
      child: child,
    );

    final tap = onTap;
    final body = tap == null
        ? content
        : InkWell(
            onTap: tap,
            borderRadius: radius,
            focusColor: t.colorFocus.withValues(
              alpha: KdStyle.selectionTintOpacity,
            ),
            overlayColor: WidgetStateProperty.resolveWith((states) {
              if (states.contains(WidgetState.pressed)) {
                return t.colorAccent.withValues(
                  alpha: KdStyle.selectionTintOpacity,
                );
              }
              return null;
            }),
            child: content,
          );

    return Semantics(
      container: true,
      button: tap != null,
      selected: selected ? true : null,
      label: semanticLabel,
      child: Material(
        type: MaterialType.card,
        color: background,
        elevation: 0,
        shape: RoundedRectangleBorder(borderRadius: radius, side: border),
        clipBehavior: Clip.antiAlias,
        child: body,
      ),
    );
  }
}
