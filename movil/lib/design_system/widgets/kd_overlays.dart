import 'package:flutter/material.dart';

import '../tokens.g.dart';
import 'kd_button.dart';
import 'kd_style.dart';

/// Shows a bottom sheet of the design system (the native mobile pattern for
/// secondary tasks, DESIGN.md R-14): surface.overlay, radius `lg` on top,
/// shadow (it floats, R-04), a drag handle and the keyboard never covering
/// its content. Animations follow the motion tokens and are removed when the
/// platform asks to reduce motion.
Future<T?> showKdBottomSheet<T>({
  required BuildContext context,
  required WidgetBuilder builder,
  bool isDismissible = true,
}) {
  final t = KdTokens.of(context);
  return showModalBottomSheet<T>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    isDismissible: isDismissible,
    enableDrag: isDismissible,
    showDragHandle: false,
    // KdSheet paints the surface, radius and shadow itself.
    backgroundColor: t.surfaceOverlay.withValues(alpha: 0),
    barrierColor: t.textDefault.withValues(alpha: KdStyle.scrimOpacity),
    barrierLabel: 'Cerrar',
    elevation: 0,
    constraints: const BoxConstraints(maxWidth: KdStyle.overlayMaxWidth),
    sheetAnimationStyle: KdStyle.overlayAnimation(context),
    builder: builder,
  );
}

/// Content of a [showKdBottomSheet]: drag handle, [title] (announced as a
/// header), [child] and, at the end, the actions. At most one
/// [KdButtonVariant.primary] action (R-02); actions stack full width so they
/// are easy to reach with the thumb.
class KdSheet extends StatelessWidget {
  const KdSheet({
    super.key,
    required this.title,
    required this.child,
    this.actions = const [],
  });

  final String title;
  final Widget child;
  final List<KdButton> actions;

  @override
  Widget build(BuildContext context) {
    final t = KdTokens.of(context);
    final keyboard = MediaQuery.viewInsetsOf(context).bottom;
    return DecoratedBox(
      decoration: BoxDecoration(
        color: t.surfaceOverlay,
        borderRadius: BorderRadius.vertical(top: Radius.circular(t.radiusLg)),
        boxShadow: t.shadowLg,
      ),
      child: Padding(
        // Lifts the whole sheet above the keyboard.
        padding: EdgeInsets.only(bottom: keyboard),
        child: SingleChildScrollView(
          keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
          padding: EdgeInsets.fromLTRB(t.space4, t.space3, t.space4, t.space4),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Center(
                child: ExcludeSemantics(
                  child: Container(
                    width: KdStyle.sheetHandleWidth,
                    height: t.space1,
                    decoration: BoxDecoration(
                      color: t.borderStrong.color,
                      borderRadius: BorderRadius.circular(t.radiusFull),
                    ),
                  ),
                ),
              ),
              SizedBox(height: t.space4),
              Semantics(
                header: true,
                child: Text(
                  title,
                  style: t.typeTitle.copyWith(color: t.textDefault),
                ),
              ),
              SizedBox(height: t.space3),
              child,
              for (final action in actions) ...[
                SizedBox(height: t.space3),
                _expanded(action),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

KdButton _expanded(KdButton b) => KdButton(
  key: b.key,
  label: b.label,
  onPressed: b.onPressed,
  variant: b.variant,
  icon: b.icon,
  loading: b.loading,
  expand: true,
);

/// Shows a dialog of the design system: surface.overlay, radius `lg`,
/// shadow, motion tokens (none with reduced motion).
Future<T?> showKdDialog<T>({
  required BuildContext context,
  required WidgetBuilder builder,
  bool barrierDismissible = true,
}) {
  final t = KdTokens.of(context);
  return showDialog<T>(
    context: context,
    barrierDismissible: barrierDismissible,
    barrierLabel: 'Cerrar',
    barrierColor: t.textDefault.withValues(alpha: KdStyle.scrimOpacity),
    animationStyle: KdStyle.overlayAnimation(context),
    builder: builder,
  );
}

/// Dialog for a short decision: [title], one sentence [message] and up to two
/// actions. The confirming action is the primary (or destructive) one; the
/// other is a [KdButton.text] that closes the dialog («Volver», never a bare
/// «Cancelar» or «OK», DESIGN.md · Tono).
class KdDialog extends StatelessWidget {
  const KdDialog({
    super.key,
    required this.title,
    required this.message,
    required this.confirm,
    this.dismiss,
  });

  final String title;
  final String message;
  final KdButton confirm;
  final KdButton? dismiss;

  @override
  Widget build(BuildContext context) {
    final t = KdTokens.of(context);
    final secondary = dismiss;
    return Dialog(
      // The DecoratedBox below paints the surface, radius and shadow.
      backgroundColor: t.surfaceOverlay.withValues(alpha: 0),
      surfaceTintColor: t.surfaceOverlay.withValues(alpha: 0),
      elevation: 0,
      insetPadding: EdgeInsets.all(t.space6),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(t.radiusLg),
      ),
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: t.surfaceOverlay,
          borderRadius: BorderRadius.circular(t.radiusLg),
          boxShadow: t.shadowLg,
        ),
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: KdStyle.overlayMaxWidth),
          child: Padding(
            padding: EdgeInsets.all(t.space6),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Semantics(
                  header: true,
                  child: Text(
                    title,
                    style: t.typeTitle.copyWith(color: t.textDefault),
                  ),
                ),
                SizedBox(height: t.space2),
                Text(message, style: t.typeBody.copyWith(color: t.textMuted)),
                SizedBox(height: t.space6),
                _expanded(confirm),
                if (secondary != null) ...[
                  SizedBox(height: t.space2),
                  _expanded(secondary),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}
