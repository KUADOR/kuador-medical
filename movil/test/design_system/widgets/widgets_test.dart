import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:medical_kuador/design_system/tokens.g.dart';
import 'package:medical_kuador/design_system/widgets/widgets.dart';

import '../../support/design_system_harness.dart';
import '../../support/style_guard.dart';
import 'gallery.dart';

Future<void> pumpThemed(
  WidgetTester tester,
  Widget child, {
  ThemeMode mode = ThemeMode.light,
}) async {
  await tester.pumpWidget(themedApp(Scaffold(body: child), mode: mode));
  await settleWithAssets(tester);
}

KdTokens tokensOf(WidgetTester tester, Finder finder) =>
    KdTokens.of(tester.element(finder));

void main() {
  group('style rules', () {
    test('widgets read styles only from Theme and KdTokens', () {
      final violations = scanLib(root: 'lib/design_system/widgets');
      expect(violations, isEmpty, reason: violations.join('\n'));
    });
  });

  group('KdIcon', () {
    final assets = Directory('assets/icons')
        .listSync()
        .whereType<File>()
        .map((f) => f.uri.pathSegments.last)
        .where((n) => n.endsWith('.svg'))
        .map((n) => n.substring(0, n.length - 4))
        .toSet();

    test('KdIcons lists exactly the SVG files of assets/icons', () {
      expect(KdIcons.all.map((i) => i.name).toSet(), assets);
      expect(KdIcons.all.length, assets.length, reason: 'no duplicates');
    });

    test('assets/icons is an exact copy of design/icons (one icon set)', () {
      final design = Directory('../design/icons');
      if (!design.existsSync()) return; // Only the movil folder checked out.
      final sources = design.listSync().whereType<File>().where(
        (f) => f.path.endsWith('.svg'),
      );
      final names = <String>{};
      for (final source in sources) {
        final name = source.uri.pathSegments.last;
        names.add(name.substring(0, name.length - 4));
        final copy = File('assets/icons/$name');
        expect(copy.existsSync(), isTrue, reason: name);
        expect(
          copy.readAsStringSync(),
          source.readAsStringSync(),
          reason: name,
        );
      }
      expect(assets, names);
    });

    testWidgets('is decorative unless it has a label', (tester) async {
      final handle = tester.ensureSemantics();
      await pumpThemed(
        tester,
        const Row(
          children: [
            KdIcon(KdIcons.bell),
            KdIcon(KdIcons.info, semanticLabel: 'Información'),
          ],
        ),
      );
      expect(find.bySemanticsLabel('Información'), findsOneWidget);
      final decorative = tester.widget<KdIcon>(find.byType(KdIcon).first);
      expect(decorative.semanticLabel, isNull);
      expect(
        find.descendant(
          of: find.byType(KdIcon).first,
          matching: find.byType(ExcludeSemantics),
        ),
        findsOneWidget,
      );
      handle.dispose();
    });

    testWidgets('draws the SVG in the IconTheme color and size', (
      tester,
    ) async {
      await pumpThemed(
        tester,
        Builder(
          builder: (context) => IconTheme(
            data: IconThemeData(
              color: KdTokens.of(context).colorAccent,
              size: KdConstants.space8,
            ),
            child: const KdIcon(KdIcons.calendar),
          ),
        ),
      );
      expect(tester.getSize(find.byType(KdIcon)), const Size.square(32));
      expect(find.byType(KdIcon), findsOneWidget);
    });
  });

  group('KdButton', () {
    testWidgets('is at least 44 tall and wide, with a 48 tap area', (
      tester,
    ) async {
      await pumpThemed(
        tester,
        Center(
          child: KdButton.text(label: 'Ir', onPressed: () {}),
        ),
      );
      final visual = tester.getSize(
        find.descendant(
          of: find.byType(TextButton),
          matching: find.byType(Material),
        ),
      );
      expect(visual.height, greaterThanOrEqualTo(44));
      expect(visual.width, greaterThanOrEqualTo(44));
      final target = tester.getSize(find.byType(TextButton));
      expect(target.height, greaterThanOrEqualTo(48));
    });

    testWidgets('primary is filled with the accent, destructive with error', (
      tester,
    ) async {
      await pumpThemed(
        tester,
        Column(
          children: [
            KdButton(label: 'Confirmar cita', onPressed: () {}),
            KdButton.destructive(label: 'Cancelar cita', onPressed: () {}),
          ],
        ),
      );
      final t = tokensOf(tester, find.byType(Column));
      Color fill(String label) => tester
          .widget<Material>(
            find
                .ancestor(of: find.text(label), matching: find.byType(Material))
                .first,
          )
          .color!;
      expect(fill('Confirmar cita'), t.colorAccent);
      expect(fill('Cancelar cita'), t.colorError);
    });

    testWidgets('calls back; disabled when onPressed is null or loading', (
      tester,
    ) async {
      var taps = 0;
      // The loading spinner never settles: pump frames by hand.
      await tester.pumpWidget(
        themedApp(
          Scaffold(
            body: Column(
              children: [
                KdButton(label: 'Activo', onPressed: () => taps++),
                const KdButton(label: 'Inactivo', onPressed: null),
                KdButton(
                  label: 'Guardando',
                  loading: true,
                  onPressed: () => taps++,
                ),
              ],
            ),
          ),
        ),
      );
      await tester.pump();
      await tester.tap(find.text('Activo'));
      await tester.tap(find.text('Inactivo'), warnIfMissed: false);
      await tester.pump();
      await tester.tap(find.text('Guardando'), warnIfMissed: false);
      await tester.pump();
      expect(taps, 1);
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
      expect(find.text('Guardando'), findsOneWidget);
    });

    testWidgets('loading shows a static indicator with reduced motion', (
      tester,
    ) async {
      simulateReducedMotion(tester);
      await pumpThemed(
        tester,
        KdButton(label: 'Guardando', loading: true, onPressed: () {}),
      );
      expect(find.byType(CircularProgressIndicator), findsNothing);
      expect(tester.binding.hasScheduledFrame, isFalse);
      expect(find.text('Guardando'), findsOneWidget);
    });

    testWidgets('shows the focus ring (color.focus, 2 px) on keyboard focus', (
      tester,
    ) async {
      final focus = FocusNode();
      addTearDown(focus.dispose);
      await pumpThemed(
        tester,
        Center(
          child: KdButton.secondary(label: 'Cambiar fecha', onPressed: () {}),
        ),
      );
      final t = tokensOf(tester, find.byType(OutlinedButton));
      final button = tester.widget<OutlinedButton>(find.byType(OutlinedButton));
      final side = button.style!.side!.resolve({WidgetState.focused})!;
      expect(side.color, t.colorFocus);
      expect(side.width, 2);
      expect(button.style!.side!.resolve({})!, t.borderStrong);
    });

    testWidgets('state animations use the motion token, none when reduced', (
      tester,
    ) async {
      await pumpThemed(tester, KdButton(label: 'Ir', onPressed: () {}));
      var style = tester.widget<FilledButton>(find.byType(FilledButton)).style!;
      expect(style.animationDuration, KdConstants.motionDurationFast);

      simulateReducedMotion(tester);
      await pumpThemed(tester, KdButton(label: 'Ir', onPressed: () {}));
      style = tester.widget<FilledButton>(find.byType(FilledButton)).style!;
      expect(style.animationDuration, Duration.zero);
    });
  });

  group('KdTextField', () {
    testWidgets('shows its label, helper and error as text', (tester) async {
      await pumpThemed(
        tester,
        const Column(
          children: [
            KdTextField(label: 'Correo', helperText: 'Usa tu correo personal.'),
            KdTextField(label: 'Teléfono', errorText: 'Falta el teléfono.'),
          ],
        ),
      );
      expect(find.text('Correo'), findsOneWidget);
      expect(find.text('Usa tu correo personal.'), findsOneWidget);
      expect(find.text('Falta el teléfono.'), findsOneWidget);
    });

    testWidgets('email and url fields do not autocorrect by default', (
      tester,
    ) async {
      await pumpThemed(
        tester,
        const Column(
          children: [
            KdTextField(label: 'Nombre'),
            KdTextField(
              label: 'Correo',
              keyboardType: TextInputType.emailAddress,
            ),
            KdTextField(label: 'Sitio', keyboardType: TextInputType.url),
            KdTextField(label: 'Código', autocorrect: false),
          ],
        ),
      );
      List<bool> autocorrect() => tester
          .widgetList<EditableText>(find.byType(EditableText))
          .map((e) => e.autocorrect)
          .toList();
      expect(autocorrect(), [true, false, false, false]);
    });

    testWidgets('the password toggle shows and hides the text', (tester) async {
      await pumpThemed(
        tester,
        const KdTextField(label: 'Contraseña', obscureText: true),
      );
      EditableText editable() =>
          tester.widget<EditableText>(find.byType(EditableText));
      expect(editable().obscureText, isTrue);
      expect(find.byTooltip('Mostrar contraseña'), findsOneWidget);
      await tester.tap(find.byTooltip('Mostrar contraseña'));
      await settleWithAssets(tester);
      expect(editable().obscureText, isFalse);
      expect(find.byTooltip('Ocultar contraseña'), findsOneWidget);
    });

    testWidgets('«next» on the keyboard moves to the next field', (
      tester,
    ) async {
      var submitted = '';
      await pumpThemed(
        tester,
        KdFormScrollView(
          children: [
            const KdTextField(label: 'Nombre'),
            KdTextField(
              label: 'Apellido',
              textInputAction: TextInputAction.done,
              onSubmitted: (v) => submitted = v,
            ),
          ],
        ),
      );
      await tester.tap(find.byType(TextField).first);
      await tester.pump();
      expect(
        tester.widget<TextField>(find.byType(TextField).first).textInputAction,
        TextInputAction.next,
      );
      await tester.testTextInput.receiveAction(TextInputAction.next);
      await tester.pump();
      final second = tester.widget<EditableText>(
        find.byType(EditableText).last,
      );
      expect(second.focusNode.hasFocus, isTrue);
      await tester.enterText(find.byType(TextField).last, 'Ejemplo');
      await tester.testTextInput.receiveAction(TextInputAction.done);
      await tester.pump();
      expect(submitted, 'Ejemplo');
    });

    testWidgets('a focused field scrolls above the keyboard', (tester) async {
      tester.view.physicalSize = const Size(360, 640);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(
        themedApp(
          Scaffold(
            body: KdFormScrollView(
              children: [
                for (var i = 1; i <= 8; i++) KdTextField(label: 'Campo $i'),
              ],
            ),
          ),
        ),
      );
      await settleWithAssets(tester);

      // The keyboard opens and takes 300 px at the bottom.
      tester.view.viewInsets = const FakeViewPadding(bottom: 300);
      await tester.pumpAndSettle();
      await tester.showKeyboard(find.byType(TextField).last);
      await tester.pumpAndSettle();

      final field = tester.getRect(find.byType(TextField).last);
      expect(field.bottom, lessThanOrEqualTo(640 - 300));
      expect(field.top, greaterThanOrEqualTo(0));
    });

    testWidgets('dragging the form hides the keyboard', (tester) async {
      await pumpThemed(
        tester,
        const KdFormScrollView(children: [KdTextField(label: 'Correo')]),
      );
      final scroll = tester.widget<SingleChildScrollView>(
        find.byType(SingleChildScrollView),
      );
      expect(
        scroll.keyboardDismissBehavior,
        ScrollViewKeyboardDismissBehavior.onDrag,
      );
    });

    testWidgets('uses the input formatters and capitalization given', (
      tester,
    ) async {
      final controller = TextEditingController();
      addTearDown(controller.dispose);
      await pumpThemed(
        tester,
        KdTextField(
          label: 'Cédula',
          controller: controller,
          keyboardType: TextInputType.number,
          inputFormatters: [FilteringTextInputFormatter.digitsOnly],
        ),
      );
      await tester.enterText(find.byType(TextField), '12a3');
      expect(controller.text, '123');
    });
  });

  group('KdListTile and KdCard', () {
    testWidgets('rows are at least 56 tall and announce selection', (
      tester,
    ) async {
      final handle = tester.ensureSemantics();
      var tapped = false;
      await pumpThemed(
        tester,
        KdListSection(
          title: 'Especialidades',
          children: [
            KdListTile(title: 'Medicina general', onTap: () => tapped = true),
            const KdListTile(title: 'Odontología', selected: true),
          ],
        ),
      );
      expect(
        tester.getSize(find.byType(KdListTile).first).height,
        greaterThanOrEqualTo(56),
      );
      expect(
        tester.getSemantics(find.byType(KdListTile).first),
        matchesSemantics(
          label: 'Medicina general',
          isButton: true,
          hasTapAction: true,
          isFocusable: true,
          hasFocusAction: true,
        ),
      );
      expect(
        tester.getSemantics(find.byType(KdListTile).last),
        isSemantics(label: 'Odontología', isSelected: true),
      );
      expect(
        tester.getSemantics(find.text('Especialidades')),
        isSemantics(isHeader: true),
      );
      await tester.tap(find.text('Medicina general'));
      expect(tapped, isTrue);
      handle.dispose();
    });

    testWidgets('tappable rows show a chevron, static rows do not', (
      tester,
    ) async {
      await pumpThemed(
        tester,
        Column(
          children: [
            KdListTile(title: 'Con acción', onTap: () {}),
            const KdListTile(title: 'Sin acción'),
          ],
        ),
      );
      final chevrons = tester
          .widgetList<KdIcon>(find.byType(KdIcon))
          .where((i) => i.icon == KdIcons.chevronRight);
      expect(chevrons, hasLength(1));
    });

    testWidgets('cards use surface.raised, radius lg, hairline and no shadow', (
      tester,
    ) async {
      await pumpThemed(tester, const KdCard(child: Text('Cita')));
      final t = tokensOf(tester, find.byType(KdCard));
      final material = tester.widget<Material>(
        find.descendant(
          of: find.byType(KdCard),
          matching: find.byType(Material),
        ),
      );
      expect(material.color, t.surfaceRaised);
      expect(material.elevation, 0);
      final shape = material.shape! as RoundedRectangleBorder;
      expect(shape.borderRadius, BorderRadius.circular(t.radiusLg));
      expect(shape.side, t.borderHairline);
    });

    testWidgets('a tappable card is one button for screen readers', (
      tester,
    ) async {
      final handle = tester.ensureSemantics();
      var taps = 0;
      await pumpThemed(
        tester,
        KdCard(
          onTap: () => taps++,
          semanticLabel: 'Próxima cita',
          child: const Text('Lunes 7'),
        ),
      );
      expect(
        tester.getSemantics(find.byType(KdCard)),
        isSemantics(isButton: true, label: 'Próxima cita\nLunes 7'),
      );
      await tester.tap(find.byType(KdCard));
      expect(taps, 1);
      handle.dispose();
    });
  });

  group('KdBottomNavBar', () {
    Widget nav(int index, ValueChanged<int> onSelected) => Scaffold(
      bottomNavigationBar: KdBottomNavBar(
        selectedIndex: index,
        onSelected: onSelected,
        destinations: const [
          KdNavDestination(icon: KdIcons.house, label: 'Inicio'),
          KdNavDestination(icon: KdIcons.calendar, label: 'Citas'),
          KdNavDestination(icon: KdIcons.user, label: 'Perfil'),
        ],
      ),
    );

    testWidgets('shows every label and reports the tapped destination', (
      tester,
    ) async {
      int? selected;
      await tester.pumpWidget(themedApp(nav(0, (i) => selected = i)));
      await settleWithAssets(tester);
      for (final label in ['Inicio', 'Citas', 'Perfil']) {
        expect(find.text(label), findsOneWidget);
      }
      await tester.tap(find.text('Citas'));
      expect(selected, 1);
    });

    testWidgets('marks the selected destination with the accent', (
      tester,
    ) async {
      await tester.pumpWidget(themedApp(nav(1, (_) {})));
      await settleWithAssets(tester);
      final t = tokensOf(tester, find.byType(KdBottomNavBar));
      final style = tester.widget<Text>(find.text('Citas')).style;
      final rendered = DefaultTextStyle.of(
        tester.element(find.text('Citas')),
      ).style.merge(style);
      expect(rendered.color, t.colorAccent);
    });

    testWidgets('animations follow the motion tokens, none when reduced', (
      tester,
    ) async {
      await tester.pumpWidget(themedApp(nav(0, (_) {})));
      expect(
        tester
            .widget<NavigationBar>(find.byType(NavigationBar))
            .animationDuration,
        KdConstants.motionDurationNormal,
      );
      simulateReducedMotion(tester);
      await tester.pumpWidget(themedApp(nav(0, (_) {})));
      await tester.pump();
      expect(
        tester
            .widget<NavigationBar>(find.byType(NavigationBar))
            .animationDuration,
        Duration.zero,
      );
    });
  });

  group('sheets and dialogs', () {
    Future<void> open(WidgetTester tester, Widget gallery) async {
      await tester.pumpWidget(themedApp(gallery));
      await tester.pump(); // Post-frame callback opens the overlay.
      await tester.pump(); // First frame of the route.
    }

    double sheetTop(WidgetTester tester) =>
        tester.getTopLeft(find.byType(KdSheet)).dy;

    testWidgets('the sheet slides in with the motion tokens', (tester) async {
      await open(tester, const OverlayGallery.sheet());
      await tester.pump(const Duration(milliseconds: 60));
      final during = sheetTop(tester);
      await settleWithAssets(tester);
      expect(sheetTop(tester), lessThan(during));
    });

    testWidgets('the sheet appears at once with reduced motion', (
      tester,
    ) async {
      simulateReducedMotion(tester);
      await open(tester, const OverlayGallery.sheet());
      await tester.pump(const Duration(milliseconds: 1));
      final first = sheetTop(tester);
      await settleWithAssets(tester);
      expect(sheetTop(tester), first);
    });

    testWidgets('the sheet shows title, content and full width actions', (
      tester,
    ) async {
      await open(tester, const OverlayGallery.sheet());
      await settleWithAssets(tester);
      expect(find.text('Elige un horario'), findsOneWidget);
      expect(find.text('Motivo de la consulta'), findsOneWidget);
      final sheetWidth = tester.getSize(find.byType(KdSheet)).width;
      final button = tester.getSize(find.byType(FilledButton));
      expect(button.width, greaterThan(sheetWidth / 2));
    });

    testWidgets('the sheet stays above the keyboard', (tester) async {
      tester.view.physicalSize = const Size(360, 640);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await open(tester, const OverlayGallery.sheet());
      await settleWithAssets(tester);
      tester.view.viewInsets = const FakeViewPadding(bottom: 300);
      await tester.pumpAndSettle();
      final field = tester.getRect(find.byType(TextField));
      expect(field.bottom, lessThanOrEqualTo(640 - 300));
    });

    testWidgets('the dialog fades in, and appears at once when reduced', (
      tester,
    ) async {
      double opacity() => tester
          .widgetList<FadeTransition>(
            find.ancestor(
              of: find.byType(KdDialog),
              matching: find.byType(FadeTransition),
            ),
          )
          .map((f) => f.opacity.value)
          .reduce((a, b) => a * b);

      await open(tester, const OverlayGallery.dialog());
      await tester.pump(const Duration(milliseconds: 60));
      expect(opacity(), lessThan(1));
      await settleWithAssets(tester);
      expect(opacity(), 1);
    });

    testWidgets('the dialog is fully visible on its first frame when reduced', (
      tester,
    ) async {
      simulateReducedMotion(tester);
      await open(tester, const OverlayGallery.dialog());
      await tester.pump(const Duration(milliseconds: 1));
      final fades = tester.widgetList<FadeTransition>(
        find.ancestor(
          of: find.byType(KdDialog),
          matching: find.byType(FadeTransition),
        ),
      );
      for (final f in fades) {
        expect(f.opacity.value, 1);
      }
    });

    testWidgets('the dialog offers one confirming action and a way back', (
      tester,
    ) async {
      await open(tester, const OverlayGallery.dialog());
      await settleWithAssets(tester);
      expect(find.text('¿Cancelar la cita?'), findsOneWidget);
      expect(find.byType(FilledButton), findsOneWidget);
      expect(find.widgetWithText(TextButton, 'Volver'), findsOneWidget);
    });
  });

  group('states', () {
    testWidgets('empty state shows its texts and its action', (tester) async {
      await tester.pumpWidget(themedApp(const EmptyGallery()));
      await settleWithAssets(tester);
      expect(find.text('Aún no tienes citas'), findsOneWidget);
      expect(find.text('Agendar cita'), findsOneWidget);
    });

    testWidgets('error state says what happened and retries', (tester) async {
      var retries = 0;
      await pumpThemed(tester, KdErrorState(onRetry: () => retries++));
      expect(find.text('No pudimos cargar la información'), findsOneWidget);
      expect(
        find.text('Revisa tu conexión e inténtalo de nuevo.'),
        findsOneWidget,
      );
      await tester.tap(find.text('Reintentar'));
      expect(retries, 1);
      final t = tokensOf(tester, find.byType(KdErrorState));
      final icon = tester.widget<KdIcon>(
        find
            .descendant(
              of: find.byType(KdErrorState),
              matching: find.byType(KdIcon),
            )
            .first,
      );
      expect(icon.icon, KdIcons.circleAlert);
      expect(icon.color, t.colorError);
    });

    testWidgets('loading state is announced as a live region', (tester) async {
      final handle = tester.ensureSemantics();
      await tester.pumpWidget(themedApp(const LoadingGallery()));
      await tester.pump();
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
      expect(
        tester.getSemantics(find.text('Cargando…')),
        isSemantics(isLiveRegion: true),
      );
      handle.dispose();
    });

    testWidgets('loading state does not move with reduced motion', (
      tester,
    ) async {
      simulateReducedMotion(tester);
      await tester.pumpWidget(themedApp(const LoadingGallery()));
      await settleWithAssets(tester);
      expect(find.byType(CircularProgressIndicator), findsNothing);
      expect(find.text('Cargando…'), findsOneWidget);
      expect(tester.binding.hasScheduledFrame, isFalse);
    });
  });

  group('KdProgressIndicator', () {
    testWidgets('spins inline and is announced with its label', (tester) async {
      final handle = tester.ensureSemantics();
      await tester.pumpWidget(
        themedApp(
          const Scaffold(
            body: Center(child: KdProgressIndicator(semanticLabel: 'Abriendo')),
          ),
        ),
      );
      await tester.pump();
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
      expect(find.bySemanticsLabel('Abriendo'), findsOneWidget);
      final box = tester.getSize(find.byType(CircularProgressIndicator));
      expect(box.width, lessThanOrEqualTo(KdConstants.space6));
      handle.dispose();
    });

    testWidgets('is a static icon with reduced motion', (tester) async {
      simulateReducedMotion(tester);
      await tester.pumpWidget(
        themedApp(const Scaffold(body: Center(child: KdProgressIndicator()))),
      );
      await settleWithAssets(tester);
      expect(find.byType(CircularProgressIndicator), findsNothing);
      expect(find.byType(KdIcon), findsOneWidget);
      expect(tester.binding.hasScheduledFrame, isFalse);
    });
  });

  testWidgets('every widget renders in dark with the dark tokens', (
    tester,
  ) async {
    await tester.pumpWidget(
      themedApp(const ListGallery(), mode: ThemeMode.dark),
    );
    await settleWithAssets(tester);
    final t = tokensOf(tester, find.byType(KdCard).first);
    expect(t, same(KdTokens.dark));
    final material = tester.widget<Material>(
      find.descendant(
        of: find.byType(KdCard).first,
        matching: find.byType(Material),
      ),
    );
    expect(material.color, KdTokens.dark.surfaceRaised);
    expect(tester.takeException(), isNull);
  });
}
