import 'package:flutter/material.dart';

import '../../../app_scope.dart';
import '../../../core/api/api_exception.dart';
import '../../../core/connectivity/connectivity_widgets.dart';
import '../../../core/session/session_models.dart';
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
      icon: Icons.local_hospital_outlined,
      busyId: _busyId,
      error: _error,
      onSelect: _select,
    );
  }
}

/// List of clinics or branches to choose from, shared by the login steps.
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
  final IconData icon;
  final String? busyId;
  final ApiException? error;
  final ValueChanged<NamedRef> onSelect;

  @override
  Widget build(BuildContext context) {
    final online = ConnectivityScope.of(context).isOnline;
    final expired = error?.code == 'INVALID_SESSION_TOKEN';
    return AuthPage(
      title: title,
      children: [
        Text(intro, style: Theme.of(context).textTheme.bodyLarge),
        const SizedBox(height: 16),
        ErrorMessage(error == null ? null : authErrorMessage(error!)),
        if (expired)
          FilledButton(
            onPressed: () =>
                Navigator.of(context).popUntil((route) => route.isFirst),
            child: const Text('Volver a iniciar sesión'),
          )
        else
          Card(
            child: Column(
              children: [
                for (final item in items)
                  ListTile(
                    leading: Icon(icon),
                    title: Text(item.name),
                    trailing: busyId == item.id
                        ? const SizedBox.square(
                            dimension: 20,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.chevron_right),
                    enabled: online && busyId == null,
                    onTap: () => onSelect(item),
                  ),
              ],
            ),
          ),
      ],
    );
  }
}
