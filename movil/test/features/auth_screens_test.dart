import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:medical_kuador/core/session/session_models.dart';
import 'package:medical_kuador/design_system/tokens.g.dart';
import 'package:medical_kuador/design_system/widgets/widgets.dart';
import 'package:medical_kuador/features/auth/data/auth_models.dart';
import 'package:medical_kuador/features/auth/ui/branch_selection_screen.dart';
import 'package:medical_kuador/features/auth/ui/clinic_selection_screen.dart';
import 'package:medical_kuador/features/auth/ui/email_verification_screen.dart';
import 'package:medical_kuador/features/auth/ui/invitation_screen.dart';
import 'package:medical_kuador/features/auth/ui/login_screen.dart';
import 'package:medical_kuador/features/auth/ui/password_reset_confirm_screen.dart';
import 'package:medical_kuador/features/auth/ui/password_reset_request_screen.dart';
import 'package:medical_kuador/features/auth/ui/self_registration_screen.dart';

import '../support/design_system_harness.dart';
import '../support/fake_api.dart';
import '../support/screen_harness.dart';

/// Redesign of the patient sign-in and sign-up screens (MEDK-45): one main
/// action per screen, large buttons, native keyboard behavior, only widgets
/// of the design system, AA in light and dark, and goldens. The flows and the
/// api calls are covered by auth_flow_test.dart and
/// self_registration_test.dart. Fictitious data only (FR-014).

const _token = 'one-time-example-token-0123456789';

const _clinics = [
  NamedRef(id: clinicA, name: 'Example Clinic'),
  NamedRef(id: clinicB, name: 'Sample Dental'),
];

const _branches = [
  NamedRef(id: branchMain, name: 'Main branch'),
  NamedRef(id: branchNorth, name: 'North branch'),
];

FakeApi _invitationApi() => FakeApi()
  ..on(
    'POST /auth/invitations/preview',
    (_) => json({
      'clinicName': 'Example Clinic',
      'maskedEmail': 'j***@example.com',
      'kind': 'PATIENT',
      'expiresAt': '2030-01-04T15:00:00Z',
    }),
  );

final List<ScreenCase> _cases = [
  ScreenCase('login', () => const LoginScreen()),
  ScreenCase(
    'login_error',
    () => const LoginScreen(),
    api: () =>
        FakeApi()
          ..on('POST /auth/login', (_) => problem('INVALID_CREDENTIALS', 401)),
    prepare: (tester) async {
      await enterText(tester, 'Correo', 'jane.doe@example.com');
      await enterText(tester, 'Contraseña', 'a-wrong-example-passphrase');
      await tapText(tester, 'Ingresar');
    },
  ),
  ScreenCase('login_offline', () => const LoginScreen(), online: false),
  ScreenCase(
    'clinic_selection',
    () => const ClinicSelectionScreen(
      step: ClinicSelectionRequired(preAuthToken: 'pre-1', clinics: _clinics),
    ),
  ),
  ScreenCase(
    'branch_selection',
    () => const BranchSelectionScreen(
      step: BranchSelectionRequired(
        preAuthToken: 'pre-2',
        clinic: NamedRef(id: clinicA, name: 'Example Clinic'),
        branches: _branches,
      ),
    ),
  ),
  ScreenCase(
    'self_registration',
    () => SelfRegistrationScreen(clock: () => now),
    size: const Size(390, 1640),
  ),
  ScreenCase(
    'invitation',
    () => const InvitationScreen(token: _token),
    api: _invitationApi,
  ),
  ScreenCase(
    'invitation_invalid',
    () => const InvitationScreen(token: _token),
    api: () => FakeApi()
      ..on(
        'POST /auth/invitations/preview',
        (_) => problem('ONE_TIME_TOKEN_INVALID', 400),
      ),
  ),
  ScreenCase(
    'email_verification',
    () => const EmailVerificationScreen(token: _token),
  ),
  ScreenCase('reset_request', () => const PasswordResetRequestScreen()),
  ScreenCase(
    'reset_request_sent',
    () => const PasswordResetRequestScreen(),
    api: () =>
        FakeApi()
          ..on('POST /auth/password-reset/request', (_) => json(null, 202)),
    prepare: (tester) async {
      await enterText(tester, 'Correo', 'jane.doe@example.com');
      await tapText(tester, 'Enviar enlace');
    },
  ),
  ScreenCase(
    'reset_confirm',
    () => const PasswordResetConfirmScreen(token: _token),
  ),
];

/// Screens whose content is a form or a confirmation: they show exactly one
/// filled (primary) button. The selection lists act on the row itself.
const _withMainAction = {
  'login',
  'login_error',
  'login_offline',
  'self_registration',
  'invitation',
  'invitation_invalid',
  'email_verification',
  'reset_request',
  'reset_request_sent',
  'reset_confirm',
};

ScreenCase _case(String name) => _cases.firstWhere((c) => c.name == name);

/// Sets a phone-sized view (390 × 844) with an optional keyboard height.
void _phoneView(WidgetTester tester, {double keyboard = 0}) {
  const ratio = 3.0;
  tester.view.devicePixelRatio = ratio;
  tester.view.physicalSize = const Size(390 * ratio, 844 * ratio);
  tester.view.viewInsets = FakeViewPadding(bottom: keyboard * ratio);
  addTearDown(tester.view.reset);
}

void main() {
  group('one main action per screen (R-02)', () {
    for (final c in _cases) {
      testWidgets(c.name, (tester) async {
        _phoneView(tester);
        await pumpCase(tester, c);

        final primary = find.byType(FilledButton);
        if (!_withMainAction.contains(c.name)) {
          expect(primary, findsNothing);
          return;
        }
        expect(primary, findsOneWidget);
        // A large button: full width of the page and at least 44 tall.
        final rect = tester.getRect(primary);
        expect(rect.height, greaterThanOrEqualTo(KdConstants.sizeTargetMin));
        expect(
          rect.width,
          greaterThanOrEqualTo(390 - 2 * KdConstants.space4 - 1),
        );
      });
    }
  });

  group('only widgets of the design system', () {
    for (final c in _cases) {
      testWidgets(c.name, (tester) async {
        await pumpCase(tester, c);

        for (final field in tester.widgetList(find.byType(TextFormField))) {
          expect(
            find.ancestor(
              of: find.byWidget(field),
              matching: find.byType(KdTextField),
            ),
            findsOneWidget,
            reason: 'every text field is a KdTextField',
          );
        }
        for (final button in tester.widgetList(
          find.byWidgetPredicate((w) => w is ButtonStyleButton),
        )) {
          final inField = find.ancestor(
            of: find.byWidget(button),
            matching: find.byType(KdTextField),
          );
          if (inField.evaluate().isNotEmpty) continue;
          expect(
            find.ancestor(
              of: find.byWidget(button),
              matching: find.byType(KdButton),
            ),
            findsOneWidget,
            reason: 'every button is a KdButton',
          );
        }
        expect(find.byType(DropdownButtonFormField<String>), findsNothing);
        expect(find.byType(Icon), findsNothing, reason: 'icons are KdIcon');
      });
    }
  });

  group('keyboard', () {
    testWidgets('login: «next» goes to the password and «done» signs in', (
      tester,
    ) async {
      final api = FakeApi()
        ..on('POST /auth/login', (_) => problem('INVALID_CREDENTIALS', 401));
      await pumpScreen(tester, const LoginScreen(), api: api);

      TextField field(String label) => tester.widget<TextField>(
        find.descendant(
          of: find.widgetWithText(KdTextField, label),
          matching: find.byType(TextField),
        ),
      );
      expect(field('Correo').textInputAction, TextInputAction.next);
      expect(field('Correo').keyboardType, TextInputType.emailAddress);
      expect(field('Contraseña').textInputAction, TextInputAction.done);

      await enterText(tester, 'Correo', 'jane.doe@example.com');
      await enterText(tester, 'Contraseña', 'a-long-example-passphrase');
      await tester.testTextInput.receiveAction(TextInputAction.done);
      await tester.pumpAndSettle();

      expect(api.calls('POST /auth/login'), hasLength(1));
    });

    testWidgets('self-registration: the last field closes with «done»', (
      tester,
    ) async {
      await pumpScreen(tester, SelfRegistrationScreen(clock: () => now));

      final fields = tester
          .widgetList<TextField>(find.byType(TextField))
          .toList();
      final password = tester.widget<TextField>(
        find.descendant(
          of: find.widgetWithText(KdTextField, 'Contraseña'),
          matching: find.byType(TextField),
        ),
      );
      expect(fields.last, same(password));
      expect(password.textInputAction, TextInputAction.done);
      final typed = fields.where((f) => !f.readOnly);
      for (final f in typed.take(typed.length - 1)) {
        expect(f.textInputAction, TextInputAction.next);
      }
    });

    for (final name in ['login', 'self_registration', 'reset_confirm']) {
      testWidgets('$name: the main action stays above the keyboard', (
        tester,
      ) async {
        _phoneView(tester, keyboard: 336);
        await pumpCase(tester, _case(name));

        final rect = tester.getRect(find.byType(FilledButton));
        expect(rect.bottom, lessThanOrEqualTo(844 - 336));
        expect(rect.top, greaterThan(0));
      });
    }
  });

  group('large text (200 %)', () {
    for (final name in ['login', 'self_registration', 'clinic_selection']) {
      testWidgets('$name does not overflow', (tester) async {
        _phoneView(tester);
        tester.platformDispatcher.textScaleFactorTestValue = 2;
        addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
        await pumpCase(tester, _case(name));

        expect(tester.takeException(), isNull);
      });
    }
  });

  group('accessibility (tap targets, labels, AA contrast)', () {
    for (final c in _cases) {
      for (final mode in const [ThemeMode.light, ThemeMode.dark]) {
        testWidgets('${c.name} in ${mode.name}', (tester) async {
          await pumpCase(tester, c, mode: mode);
          await expectMeetsA11yGuidelines(tester);
        });
      }
    }
  });

  group('goldens', () {
    for (final c in _cases) {
      testScreenGoldens('auth', c);
    }
  });
}
