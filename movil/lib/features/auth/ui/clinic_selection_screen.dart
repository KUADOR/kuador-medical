import 'package:flutter/material.dart';

import '../../../app_scope.dart';
import '../../../core/api/api_exception.dart';
import '../../../core/connectivity/connectivity_widgets.dart';
import '../../../core/session/session_models.dart';
import '../../../design_system/theme.dart';
import '../../../design_system/tokens.g.dart';
import '../../../design_system/widgets/widgets.dart';
import '../data/auth_models.dart';
import 'auth_messages.dart';
import 'auth_widgets.dart';
import 'login_flow.dart';

/// Lists only the clinics where the password matched (ADR-012).
class ClinicSelectionScreen extends StatefulWidget {
  const ClinicSelectionScreen({super.key, required this.step});

  final ClinicSelectionRequired step;

  @override
  State<ClinicSelectionScreen> createState() => _ClinicSelectionScreenState();
}

class _ClinicSelectionScreenState extends State<ClinicSelectionScreen> {
  String? _busyId;
  ApiException? _error;

  Future<void> _select(NamedRef clinic) async {
    if (_busyId != null) return;
    final monitor = ConnectivityScope.of(context);
    if (!await monitor.check() || !mounted) return;
    setState(() {
      _busyId = clinic.id;
      _error = null;
    });
    try {
      final result = await AppScope.of(context).authApi.selectClinic(
        preAuthToken: widget.step.preAuthToken,
        clinicId: clinic.id,
      );
      if (!mounted) return;
      await continueLogin(context, result);
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e);
    } finally {
      if (mounted) setState(() => _busyId = null);
    }
  }

  @override
  Widget build(BuildContext context) {
    return SelectionList(
      title: 'Elige la clínica',
      intro:
          'Tu correo y contraseña coinciden en varias clínicas. Cada una tiene una cuenta independiente.',
      items: widget.step.clinics,
      icon: KdIcons.building,
      busyId: _busyId,
      error: _error,
      onSelect: _select,
    );
  }
}

/// List of clinics or branches to choose from, shared by the login steps.
///
/// Each row is the action (no extra confirm button): a tap chooses it and
/// shows its progress in place. Rows are disabled while offline or busy.
class SelectionList extends StatelessWidget {
  const SelectionList({
    super.key,
    required this.title,
    required this.intro,
    required this.items,
    required this.icon,
    required this.busyId,
    required this.error,
    required this.onSelect,
  });

  final String title;
  final String intro;
  final List<NamedRef> items;
  final KdIconData icon;
  final String? busyId;
  final ApiException? error;
  final ValueChanged<NamedRef> onSelect;

  @override
  Widget build(BuildContext context) {
    final t = KdTokens.of(context);
    final online = ConnectivityScope.of(context).isOnline;
    final failure = error;
    final expired = failure?.code == 'INVALID_SESSION_TOKEN';
    final enabled = online && busyId == null;
    return AuthPage(
      title: title,
      primaryAction: expired
          ? KdButton(
              label: 'Volver a iniciar sesión',
              expand: true,
              onPressed: () =>
                  Navigator.of(context).popUntil((route) => route.isFirst),
            )
          : null,
      children: [
        Text(intro, style: t.typeBody.copyWith(color: t.textMuted)),
        if (failure != null) KdInlineMessage.error(authErrorMessage(failure)),
        if (!expired)
          KdListSection(
            children: [
              for (final item in items)
                KdListTile(
                  title: item.name,
                  leadingIcon: icon,
                  trailing: busyId == item.id
                      ? _Progress(color: t.colorAccent, size: t.space6)
                      : null,
                  onTap: enabled ? () => onSelect(item) : null,
                ),
            ],
          ),
      ],
    );
  }
}

/// Progress of the chosen row; a static clock when the platform reduces
/// motion (DESIGN.md, R-13).
class _Progress extends StatelessWidget {
  const _Progress({required this.color, required this.size});

  final Color color;
  final double size;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: 'Ingresando',
      child: KdTheme.reduceMotion(context)
          ? KdIcon(KdIcons.clock, color: color, size: size)
          : SizedBox.square(
              dimension: size,
              child: CircularProgressIndicator(color: color),
            ),
    );
  }
}
