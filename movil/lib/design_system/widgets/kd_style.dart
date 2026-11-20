import 'package:flutter/material.dart';

import '../theme.dart';
import '../tokens.g.dart';

/// Shared styling rules of the widget library, derived only from the tokens.
///
/// Internal to `lib/design_system/`: screens use the widgets, not these
/// helpers.
abstract final class KdStyle {
  /// Share of the accent mixed into a surface for a soft selection
  /// (DESIGN.md, R-10).
  static const double selectionTintOpacity = 0.12;

  /// Width of the focus ring (DESIGN.md, R-09).
  static const double focusRingWidth = 2;

  /// Icon size inside controls and rows.
  static const double iconSize = 24;

  /// Icon size next to a label (buttons, chips).
  static const double inlineIconSize = 20;

  /// Icon size of the empty, loading and error states.
  static const double stateIconSize = 48;

  /// Width of the drag handle of a bottom sheet.
  static const double sheetHandleWidth = 32;

  /// Room kept around a focused field when it scrolls above the keyboard:
  /// its label, helper or error text and a margin.
  static const double fieldScrollPadding =
      KdConstants.space12 + KdConstants.space8;

  /// Opacity of the scrim (text.default) behind sheets and dialogs.
  static const double scrimOpacity = 0.48;

  /// Readable width cap for dialogs and sheets on tablets.
  static const double overlayMaxWidth = 560;

  /// Soft selection background: the accent mixed into [surface].
  static Color selectionTint(KdTokens t, Color surface) => Color.alphaBlend(
    t.colorAccent.withValues(alpha: selectionTintOpacity),
    surface,
  );

  /// Focus ring border (color.focus, 2 px).
  static BorderSide focusRing(KdTokens t) =>
      BorderSide(color: t.colorFocus, width: focusRingWidth);

  /// A motion token, or zero when the platform asks to reduce motion
  /// (DESIGN.md, R-13).
  static Duration motion(BuildContext context, Duration token) =>
      KdTheme.duration(context, token);

  /// Animation of a sheet or dialog entering and leaving.
  static AnimationStyle overlayAnimation(BuildContext context) {
    final d = motion(context, KdConstants.motionDurationSlow);
    return AnimationStyle(
      duration: d,
      reverseDuration: d,
      curve: KdConstants.motionEasingEnter,
      reverseCurve: KdConstants.motionEasingExit,
    );
  }
}
