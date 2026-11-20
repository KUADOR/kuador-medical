import 'package:flutter/material.dart';

import '../tokens.g.dart';
import 'kd_icon.dart';
import 'kd_list.dart';
import 'kd_overlays.dart';
import 'kd_text_field.dart';

/// One choice of a [KdSelectField]: the [value] and its Spanish [label].
@immutable
class KdSelectOption<T> {
  const KdSelectOption(this.value, this.label);

  final T value;
  final String label;
}

/// Field to choose one of a few options with the native mobile pattern: it
/// looks like a [KdTextField] (label, helper and error text, 44 tall) and a
/// tap opens a bottom sheet with the options as rows, the current one marked
/// as selected (DESIGN.md, R-10 and R-14). The keyboard never opens.
///
/// Inside a [Form], [validator] receives the selected value (not the text).
class KdSelectField<T> extends StatefulWidget {
  const KdSelectField({
    super.key,
    required this.label,
    required this.options,
    required this.value,
    required this.onChanged,
    this.validator,
    this.helperText,
    this.enabled = true,
  });

  final String label;
  final List<KdSelectOption<T>> options;

  /// Selected value, or null when nothing is chosen yet.
  final T? value;
  final ValueChanged<T?> onChanged;
  final String? Function(T? value)? validator;
  final String? helperText;
  final bool enabled;

  @override
  State<KdSelectField<T>> createState() => _KdSelectFieldState<T>();
}

class _KdSelectFieldState<T> extends State<KdSelectField<T>> {
  late final TextEditingController _text = TextEditingController(
    text: _labelOf(widget.value),
  );

  String _labelOf(T? value) {
    for (final o in widget.options) {
      if (o.value == value) return o.label;
    }
    return '';
  }

  @override
  void didUpdateWidget(KdSelectField<T> oldWidget) {
    super.didUpdateWidget(oldWidget);
    // The text changes after this frame: changing it now would rebuild the
    // enclosing Form while it is building.
    WidgetsBinding.instance.addPostFrameCallback((_) => _syncText());
  }

  void _syncText() {
    if (!mounted) return;
    final label = _labelOf(widget.value);
    if (_text.text != label) _text.text = label;
  }

  @override
  void dispose() {
    _text.dispose();
    super.dispose();
  }

  Future<void> _choose() async {
    FocusManager.instance.primaryFocus?.unfocus();
    final chosen = await showKdBottomSheet<KdSelectOption<T>>(
      context: context,
      builder: (context) => _OptionsSheet<T>(
        title: widget.label,
        options: widget.options,
        selected: widget.value,
      ),
    );
    if (chosen == null || !mounted) return;
    _text.text = chosen.label;
    widget.onChanged(chosen.value);
  }

  @override
  Widget build(BuildContext context) {
    final validate = widget.validator;
    return KdTextField(
      label: widget.label,
      controller: _text,
      helperText: widget.helperText,
      enabled: widget.enabled,
      readOnly: true,
      onTap: widget.enabled ? _choose : null,
      suffixIcon: KdIcons.chevronDown,
      validator: validate == null ? null : (_) => validate(widget.value),
    );
  }
}

class _OptionsSheet<T> extends StatelessWidget {
  const _OptionsSheet({
    required this.title,
    required this.options,
    required this.selected,
  });

  final String title;
  final List<KdSelectOption<T>> options;
  final T? selected;

  @override
  Widget build(BuildContext context) {
    final t = KdTokens.of(context);
    return KdSheet(
      title: title,
      child: KdListSection(
        children: [
          for (final o in options)
            KdListTile(
              title: o.label,
              selected: o.value == selected,
              trailing: o.value == selected
                  ? KdIcon(KdIcons.check, color: t.colorAccent)
                  : const SizedBox.shrink(),
              onTap: () => Navigator.of(context).pop(o),
            ),
        ],
      ),
    );
  }
}
