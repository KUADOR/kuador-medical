import 'package:flutter/material.dart';

import '../theme.dart';
import '../tokens.g.dart';
import 'kd_icon.dart';
import 'kd_style.dart';

/// Small inline progress indicator, for a row or a control that is busy (a
/// list row being opened). For a whole view use `KdLoadingState`; for a
/// button use `KdButton(loading: true)`.
///
/// Announced with [semanticLabel] when given; decorative otherwise. With
/// reduced motion the spinner becomes a static clock icon (DESIGN.md, R-13).
class KdProgressIndicator extends StatelessWidget {
  const KdProgressIndicator({super.key, this.semanticLabel});

  final String? semanticLabel;

  @override
  Widget build(BuildContext context) {
    final t = KdTokens.of(context);
    final Widget visual = KdTheme.reduceMotion(context)
        ? KdIcon(
            KdIcons.clock,
            size: KdStyle.inlineIconSize,
            color: t.colorAccent,
          )
        : SizedBox.square(
            dimension: KdStyle.inlineIconSize,
            child: CircularProgressIndicator(
              color: t.colorAccent,
              backgroundColor: t.surfaceSunken,
              strokeWidth: KdStyle.focusRingWidth,
            ),
          );
    final label = semanticLabel;
    if (label == null) return ExcludeSemantics(child: visual);
    return Semantics(
      label: label,
      liveRegion: true,
      child: ExcludeSemantics(child: visual),
    );
  }
}
