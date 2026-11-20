import 'package:flutter/material.dart';

import '../tokens.g.dart';
import 'kd_icon.dart';
import 'kd_style.dart';

enum _Tone { error, warning, success, info }

/// Short message inside a page (a failed submit, no connection, a
/// confirmation): surface.raised with radius `md` and a hairline border, a
/// semantic icon and the text in text.default, so the state is never told by
/// color alone (DESIGN.md, R-08). Announced to screen readers when it
/// appears.
class KdInlineMessage extends StatelessWidget {
  /// Something failed: what happened and what to do.
  const KdInlineMessage.error(this.message, {super.key}) : _tone = _Tone.error;

  /// A condition that blocks an action (no connection).
  const KdInlineMessage.warning(this.message, {super.key})
    : _tone = _Tone.warning;

  /// The action worked.
  const KdInlineMessage.success(this.message, {super.key})
    : _tone = _Tone.success;

  /// Neutral information.
  const KdInlineMessage.info(this.message, {super.key}) : _tone = _Tone.info;

  final String message;
  final _Tone _tone;

  @override
  Widget build(BuildContext context) {
    final t = KdTokens.of(context);
    final (KdIconData icon, Color color) = switch (_tone) {
      _Tone.error => (KdIcons.circleAlert, t.colorError),
      _Tone.warning => (KdIcons.triangleAlert, t.colorWarning),
      _Tone.success => (KdIcons.circleCheck, t.colorOk),
      _Tone.info => (KdIcons.info, t.colorInfo),
    };
    return Semantics(
      liveRegion: true,
      container: true,
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: t.surfaceRaised,
          borderRadius: BorderRadius.circular(t.radiusMd),
          border: Border.fromBorderSide(t.borderHairline),
        ),
        child: Padding(
          padding: EdgeInsets.all(t.space3),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              KdIcon(icon, color: color, size: KdStyle.inlineIconSize),
              SizedBox(width: t.space3),
              Expanded(
                child: Text(
                  message,
                  style: t.typeBody.copyWith(color: t.textDefault),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
