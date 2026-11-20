import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:medical_kuador/design_system/tokens.g.dart';
import 'package:medical_kuador/design_system/widgets/widgets.dart';

import '../../support/design_system_harness.dart';

/// Form controls added for the patient sign-in and sign-up screens
/// (MEDK-45): read-only picker fields, the select field with a bottom sheet
/// and the inline message. Fictitious data only (FR-014).
void main() {
  group('KdTextField as a picker', () {
    testWidgets('read-only: a tap runs onTap and never opens the keyboard', (
      tester,
    ) async {
      var taps = 0;
      await tester.pumpWidget(
        themedApp(
          Scaffold(
            body: KdFormScrollView(
              children: [
                KdTextField(
                  label: 'Fecha de nacimiento',
                  readOnly: true,
                  onTap: () => taps++,
                  suffixIcon: KdIcons.calendar,
                ),
              ],
            ),
          ),
        ),
      );
      await settleWithAssets(tester);

      await tester.tap(find.text('Fecha de nacimiento'));
      await tester.pump();

      expect(taps, 1);
      expect(tester.testTextInput.isVisible, isFalse);
      final field = tester.widget<TextField>(find.byType(TextField));
      expect(field.readOnly, isTrue);
      expect(find.byType(KdIcon), findsOneWidget);
    });
  });

  group('KdSelectField', () {
    Widget form({
      required String? value,
      required ValueChanged<String?> onChanged,
      GlobalKey<FormState>? formKey,
    }) {
      return Scaffold(
        body: Form(
          key: formKey,
          child: KdFormScrollView(
            children: [
              KdSelectField<String>(
                label: 'Tipo de documento',
                value: value,
                options: const [
                  KdSelectOption('NATIONAL_ID', 'Cédula de identidad'),
                  KdSelectOption('PASSPORT', 'Pasaporte'),
                ],
                onChanged: onChanged,
                validator: (v) => v == null ? 'Elige el tipo.' : null,
              ),
            ],
          ),
        ),
      );
    }

    testWidgets('opens a bottom sheet and returns the chosen option', (
      tester,
    ) async {
      String? value;
      await tester.pumpWidget(
        themedApp(
          StatefulBuilder(
            builder: (context, setState) =>
                form(value: value, onChanged: (v) => setState(() => value = v)),
          ),
        ),
      );
      await settleWithAssets(tester);

      await tester.tap(find.text('Tipo de documento'));
      await settleWithAssets(tester);
      expect(find.byType(KdSheet), findsOneWidget);
      expect(find.text('Pasaporte'), findsOneWidget);

      await tester.tap(find.text('Pasaporte'));
      await settleWithAssets(tester);

      expect(value, 'PASSPORT');
      expect(find.byType(KdSheet), findsNothing);
      final field = tester.widget<TextField>(find.byType(TextField));
      expect(field.controller!.text, 'Pasaporte');
    });

    testWidgets('marks the current option as selected in the sheet', (
      tester,
    ) async {
      await tester.pumpWidget(
        themedApp(form(value: 'NATIONAL_ID', onChanged: (_) {})),
      );
      await settleWithAssets(tester);

      await tester.tap(find.text('Tipo de documento'));
      await settleWithAssets(tester);

      final selected = tester.widget<KdListTile>(
        find.widgetWithText(KdListTile, 'Cédula de identidad'),
      );
      expect(selected.selected, isTrue);
      final other = tester.widget<KdListTile>(
        find.widgetWithText(KdListTile, 'Pasaporte'),
      );
      expect(other.selected, isFalse);
    });

    testWidgets('validates the selected value, not the text', (tester) async {
      final formKey = GlobalKey<FormState>();
      await tester.pumpWidget(
        themedApp(form(value: null, onChanged: (_) {}, formKey: formKey)),
      );
      await settleWithAssets(tester);

      expect(formKey.currentState!.validate(), isFalse);
      await tester.pump();
      expect(find.text('Elige el tipo.'), findsOneWidget);
    });

    testA11yGuidelines(
      'select field',
      () => form(value: 'PASSPORT', onChanged: (_) {}),
    );
  });

  group('KdInlineMessage', () {
    Widget messages() => const Scaffold(
      body: Padding(
        padding: EdgeInsets.all(KdConstants.space4),
        child: Column(
          children: [
            KdInlineMessage.error('No pudimos guardar tus datos.'),
            KdInlineMessage.warning('Sin conexión.'),
            KdInlineMessage.success('Listo. Revisa tu correo.'),
            KdInlineMessage.info('Te lo entrega la clínica.'),
          ],
        ),
      ),
    );

    testWidgets('is announced to screen readers with an icon and text', (
      tester,
    ) async {
      final handle = tester.ensureSemantics();
      await tester.pumpWidget(themedApp(messages()));
      await settleWithAssets(tester);

      expect(
        tester.getSemantics(find.text('No pudimos guardar tus datos.')),
        isSemantics(label: 'No pudimos guardar tus datos.', isLiveRegion: true),
      );
      final live = find.byWidgetPredicate(
        (w) => w is Semantics && w.properties.liveRegion == true,
      );
      expect(live, findsNWidgets(4));
      expect(find.byType(KdIcon), findsNWidgets(4));
      handle.dispose();
    });

    testA11yGuidelines('inline messages', messages);
  });
}
