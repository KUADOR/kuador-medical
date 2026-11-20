import 'package:flutter/material.dart';

import '../../../core/ui/module_widgets.dart';
import '../../../design_system/tokens.g.dart';
import '../../../design_system/widgets/widgets.dart';
import '../../auth/data/registration_catalog.dart';
import '../data/patient_models.dart';

/// Picks a calendar date (birth dates): read-only text field with a picker.
class DatePickerField extends StatelessWidget {
  const DatePickerField({
    super.key,
    required this.label,
    required this.value,
    required this.onChanged,
    required this.today,
    this.validator,
    this.enabled = true,
  });

  final String label;
  final DateTime? value;
  final ValueChanged<DateTime> onChanged;
  final DateTime today;
  final FormFieldValidator<DateTime>? validator;
  final bool enabled;

  Future<void> _pick(BuildContext context) async {
    final picked = await showDatePicker(
      context: context,
      initialDate: value ?? DateTime(today.year - 30, today.month),
      firstDate: DateTime(today.year - 120),
      lastDate: today,
      helpText: label,
    );
    if (picked != null) onChanged(picked);
  }

  @override
  Widget build(BuildContext context) {
    return FormField<DateTime>(
      key: ValueKey(value),
      initialValue: value,
      validator: validator,
      builder: (field) => TextFormField(
        key: ValueKey('text-$value'),
        initialValue: value == null ? '' : formatDate(value!),
        readOnly: true,
        enabled: enabled,
        onTap: () => _pick(context),
        decoration: fieldDecoration(label).copyWith(
          errorText: field.errorText,
          suffixIcon: IconButton(
            tooltip: 'Elegir ${label.toLowerCase()}',
            icon: const Icon(Icons.calendar_today),
            onPressed: enabled ? () => _pick(context) : null,
          ),
        ),
      ),
    );
  }
}

/// Coded catalog dropdown (document type, sex).
class CatalogDropdown extends StatelessWidget {
  const CatalogDropdown({
    super.key,
    required this.label,
    required this.options,
    required this.value,
    required this.onChanged,
    this.errorText,
  });

  final String label;
  final List<CatalogOption> options;
  final String? value;
  final ValueChanged<String?> onChanged;
  final String? errorText;

  @override
  Widget build(BuildContext context) {
    final known = options.any((o) => o.code == value);
    return DropdownButtonFormField<String>(
      initialValue: value,
      isExpanded: true,
      items: [
        for (final o in options)
          DropdownMenuItem(value: o.code, child: Text(o.label)),
        // A code the app does not know yet (catalog [POR DEFINIR]) stays visible.
        if (value != null && !known)
          DropdownMenuItem(value: value, child: Text(value!)),
      ],
      onChanged: onChanged,
      validator: (v) => v == null ? 'Elige una opción.' : errorText,
      decoration: fieldDecoration(label),
    );
  }
}

/// Data of a guardian or authorized person (`GuardianPersonInput`).
class GuardianPersonController {
  final documentNumber = TextEditingController();
  final firstNames = TextEditingController();
  final lastNames = TextEditingController();
  final email = TextEditingController();
  final phone = TextEditingController();
  String? documentType;

  GuardianPerson value() => GuardianPerson(
    documentType: documentType!,
    documentNumber: documentNumber.text.trim(),
    firstNames: firstNames.text.trim(),
    lastNames: lastNames.text.trim(),
    email: email.text.trim(),
    phone: phone.text.trim(),
  );

  void dispose() {
    for (final c in [documentNumber, firstNames, lastNames, email, phone]) {
      c.dispose();
    }
  }
}

/// Fields of a [GuardianPersonController]. [errors] holds API field errors
/// keyed by the field name inside the person object.
class GuardianPersonFields extends StatelessWidget {
  const GuardianPersonFields({
    super.key,
    required this.controller,
    this.errors = const {},
  });

  final GuardianPersonController controller;
  final Map<String, String> errors;

  FormFieldValidator<String> _v(
    String field,
    FormFieldValidator<String> local,
  ) =>
      (v) => local(v) ?? errors[field];

  @override
  Widget build(BuildContext context) {
    const gap = SizedBox(height: KdConstants.space4);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        CatalogDropdown(
          label: 'Tipo de documento del representante',
          options: RegistrationCatalog.documentTypes,
          value: controller.documentType,
          errorText: errors['documentType'],
          onChanged: (v) => controller.documentType = v,
        ),
        gap,
        KdTextField(
          label: 'Número de documento del representante',
          controller: controller.documentNumber,
          validator: _v('documentNumber', FormRules.requiredMax(30)),
        ),
        gap,
        KdTextField(
          label: 'Nombres del representante',
          controller: controller.firstNames,
          textCapitalization: TextCapitalization.words,
          validator: _v('firstNames', FormRules.requiredMax(120)),
        ),
        gap,
        KdTextField(
          label: 'Apellidos del representante',
          controller: controller.lastNames,
          textCapitalization: TextCapitalization.words,
          validator: _v('lastNames', FormRules.requiredMax(120)),
        ),
        gap,
        KdTextField(
          label: 'Correo del representante (opcional)',
          controller: controller.email,
          keyboardType: TextInputType.emailAddress,
          validator: _v('email', FormRules.optionalEmail),
        ),
        gap,
        KdTextField(
          label: 'Teléfono del representante (opcional)',
          controller: controller.phone,
          keyboardType: TextInputType.phone,
          validator: _v('phone', FormRules.optionalPhone),
        ),
      ],
    );
  }
}

/// Errors of `prefix.field` keyed by `field`.
Map<String, String> nestedErrors(Map<String, String> errors, String prefix) => {
  for (final e in errors.entries)
    if (e.key.startsWith('$prefix.'))
      e.key.substring(prefix.length + 1): e.value,
};

/// One guardian link, current or historical, with optional action.
class GuardianLinkTile extends StatelessWidget {
  const GuardianLinkTile({super.key, required this.link, this.trailing});

  final GuardianLink link;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    final period = link.validTo == null
        ? 'Vigente desde ${formatDate(link.validFrom.toLocal())}'
        : 'Del ${formatDate(link.validFrom.toLocal())} al ${formatDate(link.validTo!.toLocal())}';
    final reason = link.endReasonLabel;
    final t = KdTokens.of(context);
    final end = trailing;
    return Padding(
      padding: EdgeInsets.only(bottom: t.space2),
      child: KdCard(
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            KdIcon(
              link.current ? KdIcons.circleCheck : KdIcons.clock,
              color: link.current ? t.colorAccent : t.textMuted,
              semanticLabel: link.current ? 'Vigente' : 'Histórico',
            ),
            SizedBox(width: t.space3),
            Expanded(
              child: MergeSemantics(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      link.guardianName,
                      style: t.typeBody.copyWith(color: t.textDefault),
                    ),
                    Text(
                      [
                        '${link.linkType.label} · ${link.relationship}',
                        period,
                        ?reason,
                      ].join('\n'),
                      style: t.typeBodySm.copyWith(color: t.textMuted),
                    ),
                  ],
                ),
              ),
            ),
            if (end != null) ...[SizedBox(width: t.space2), end],
          ],
        ),
      ),
    );
  }
}
