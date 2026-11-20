import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../tokens.g.dart';
import 'kd_icon.dart';
import 'kd_style.dart';

/// Text field of the design system: visible label, token colors, `md`
/// radius, outline in border.strong (3:1), focus in color.focus and error
/// shown with text (never only color).
///
/// Keyboard: [textInputAction] defaults to «next» and moves the focus to the
/// next field; on the last field pass [TextInputAction.done] and
/// [onSubmitted]. When focused, the field scrolls above the keyboard with
/// room for its helper or error text. Put the fields in a [KdFormScrollView].
///
/// With [obscureText] it shows a «Mostrar» / «Ocultar» toggle with a 48 tap
/// area.
///
/// With [readOnly] and [onTap] it is a picker (a date, an option of a
/// [KdSelectField]): a tap runs [onTap] and the keyboard never opens.
/// [suffixIcon] is a decorative icon at the end (a calendar, a chevron).
class KdTextField extends StatefulWidget {
  const KdTextField({
    super.key,
    required this.label,
    this.controller,
    this.focusNode,
    this.hint,
    this.helperText,
    this.errorText,
    this.keyboardType,
    this.textInputAction,
    this.textCapitalization = TextCapitalization.none,
    this.autofillHints,
    this.inputFormatters,
    this.obscureText = false,
    this.autocorrect,
    this.enabled = true,
    this.autofocus = false,
    this.maxLines = 1,
    this.onChanged,
    this.onSubmitted,
    this.validator,
    this.prefixIcon,
    this.readOnly = false,
    this.onTap,
    this.suffixIcon,
  });

  final String label;
  final TextEditingController? controller;
  final FocusNode? focusNode;
  final String? hint;
  final String? helperText;

  /// Message under the field; also marks the field as invalid.
  final String? errorText;
  final TextInputType? keyboardType;
  final TextInputAction? textInputAction;
  final TextCapitalization textCapitalization;
  final Iterable<String>? autofillHints;
  final List<TextInputFormatter>? inputFormatters;
  final bool obscureText;

  /// Keyboard autocorrection. Defaults to off for passwords, emails and
  /// URLs (they are not words) and on otherwise.
  final bool? autocorrect;
  final bool enabled;
  final bool autofocus;
  final int maxLines;
  final ValueChanged<String>? onChanged;
  final ValueChanged<String>? onSubmitted;

  /// Validation inside a [Form]; its message replaces [errorText].
  final FormFieldValidator<String>? validator;
  final KdIconData? prefixIcon;

  /// The text cannot be typed; pair it with [onTap] for a picker.
  final bool readOnly;
  final VoidCallback? onTap;

  /// Decorative icon at the end; ignored with [obscureText].
  final KdIconData? suffixIcon;

  @override
  State<KdTextField> createState() => _KdTextFieldState();
}

class _KdTextFieldState extends State<KdTextField> {
  late bool _hidden = widget.obscureText;

  bool get _autocorrectByDefault {
    final type = widget.keyboardType;
    return !widget.obscureText &&
        type != TextInputType.emailAddress &&
        type != TextInputType.url;
  }

  @override
  Widget build(BuildContext context) {
    final t = KdTokens.of(context);
    final multiline = widget.maxLines != 1;
    return TextFormField(
      controller: widget.controller,
      focusNode: widget.focusNode,
      enabled: widget.enabled,
      autofocus: widget.autofocus,
      readOnly: widget.readOnly,
      onTap: widget.onTap,
      enableInteractiveSelection: !widget.readOnly,
      obscureText: _hidden,
      enableSuggestions: !widget.obscureText,
      autocorrect: widget.autocorrect ?? _autocorrectByDefault,
      keyboardType:
          widget.keyboardType ?? (multiline ? TextInputType.multiline : null),
      textInputAction:
          widget.textInputAction ??
          (multiline ? TextInputAction.newline : TextInputAction.next),
      textCapitalization: widget.textCapitalization,
      autofillHints: widget.autofillHints,
      inputFormatters: widget.inputFormatters,
      maxLines: widget.obscureText ? 1 : widget.maxLines,
      minLines: 1,
      onChanged: widget.onChanged,
      onFieldSubmitted: widget.onSubmitted,
      validator: widget.validator,
      // Keeps the label, the helper or error text and a margin visible above
      // the keyboard when the field takes the focus.
      scrollPadding: const EdgeInsets.all(KdStyle.fieldScrollPadding),
      style: t.typeBody.copyWith(color: t.textDefault),
      cursorColor: t.colorAccent,
      decoration: _decoration(t),
    );
  }

  InputDecoration _decoration(KdTokens t) {
    OutlineInputBorder outline(BorderSide side) => OutlineInputBorder(
      borderRadius: BorderRadius.circular(t.radiusMd),
      borderSide: side,
    );
    final errorSide = BorderSide(color: t.colorError);
    final prefix = widget.prefixIcon;
    final suffix = widget.suffixIcon;

    return InputDecoration(
      labelText: widget.label,
      hintText: widget.hint,
      helperText: widget.helperText,
      helperMaxLines: 3,
      errorText: widget.errorText,
      errorMaxLines: 3,
      filled: true,
      fillColor: widget.enabled ? t.surfaceRaised : t.surfaceSunken,
      isDense: false,
      contentPadding: EdgeInsets.symmetric(
        horizontal: t.space4,
        vertical: t.space3,
      ),
      constraints: BoxConstraints(minHeight: t.sizeTargetMin),
      labelStyle: t.typeBody.copyWith(color: t.textMuted),
      floatingLabelStyle: WidgetStateTextStyle.resolveWith((states) {
        final color = states.contains(WidgetState.error)
            ? t.colorError
            : states.contains(WidgetState.focused)
            ? t.colorFocus
            : t.textMuted;
        return t.typeLabel.copyWith(color: color);
      }),
      hintStyle: t.typeBody.copyWith(color: t.textMuted),
      helperStyle: t.typeBodySm.copyWith(color: t.textMuted),
      errorStyle: t.typeBodySm.copyWith(color: t.colorError),
      prefixIcon: prefix == null
          ? null
          : KdIcon(prefix, color: t.textMuted, size: KdStyle.inlineIconSize),
      suffixIcon: widget.obscureText
          ? _visibilityToggle(t)
          : suffix == null
          ? null
          : KdIcon(suffix, color: t.textMuted, size: KdStyle.inlineIconSize),
      border: outline(t.borderStrong),
      enabledBorder: outline(t.borderStrong),
      disabledBorder: outline(t.borderHairline),
      focusedBorder: outline(KdStyle.focusRing(t)),
      errorBorder: outline(errorSide),
      focusedErrorBorder: outline(
        errorSide.copyWith(width: KdStyle.focusRingWidth),
      ),
    );
  }

  Widget _visibilityToggle(KdTokens t) {
    return IconButton(
      onPressed: widget.enabled
          ? () => setState(() => _hidden = !_hidden)
          : null,
      tooltip: _hidden ? 'Mostrar contraseña' : 'Ocultar contraseña',
      color: t.textMuted,
      icon: KdIcon(_hidden ? KdIcons.eye : KdIcons.eyeOff),
    );
  }
}

/// Scrollable body for forms: fields never stay under the keyboard.
///
/// Adds the keyboard height (view insets) and the bottom safe area to its
/// end padding, so the last field and the main action can scroll above the
/// keyboard, and hides the keyboard when the user drags the content. Taps
/// outside the fields also close it.
class KdFormScrollView extends StatelessWidget {
  const KdFormScrollView({super.key, required this.children, this.controller});

  final List<Widget> children;
  final ScrollController? controller;

  @override
  Widget build(BuildContext context) {
    final t = KdTokens.of(context);
    final media = MediaQuery.of(context);
    // When the Scaffold already resizes for the keyboard, the insets here are
    // zero; when it does not (resizeToAvoidBottomInset: false or a sheet),
    // this padding keeps the content reachable.
    final bottom = t.space4 + media.viewInsets.bottom + media.padding.bottom;
    return GestureDetector(
      behavior: HitTestBehavior.translucent,
      // Closing the keyboard is a convenience for touch, not an action for
      // screen readers.
      excludeFromSemantics: true,
      onTap: () => FocusManager.instance.primaryFocus?.unfocus(),
      child: SingleChildScrollView(
        controller: controller,
        keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
        padding: EdgeInsets.fromLTRB(t.space4, t.space4, t.space4, bottom),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            for (var i = 0; i < children.length; i++) ...[
              if (i > 0) SizedBox(height: t.space4),
              children[i],
            ],
          ],
        ),
      ),
    );
  }
}
