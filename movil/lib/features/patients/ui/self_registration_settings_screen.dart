import 'package:flutter/material.dart';

import '../../../app_scope.dart';
import '../../../core/ui/module_widgets.dart';
import '../data/patient_models.dart';
import '../../../design_system/tokens.g.dart';

/// Enables or disables the patient self-registration of the clinic
/// (FR-003). Disabled by default.
class SelfRegistrationSettingsScreen extends StatefulWidget {
  const SelfRegistrationSettingsScreen({super.key});

  @override
  State<SelfRegistrationSettingsScreen> createState() =>
      _SelfRegistrationSettingsScreenState();
}

class _SelfRegistrationSettingsScreenState
    extends State<SelfRegistrationSettingsScreen> {
  Future<RegistrationSettings>? _settings;
  bool _busy = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _settings ??= AppScope.of(context).patientsApi.registrationSettings();
  }

  void _reload() => setState(() {
    _settings = AppScope.of(context).patientsApi.registrationSettings();
  });

  Future<void> _toggle(RegistrationSettings current, bool enabled) async {
    final api = AppScope.of(context).patientsApi;
    setState(() => _busy = true);
    RegistrationSettings? updated;
    await runOnline(
      context,
      () async => updated = await api.updateRegistrationSettings(
        enabled: enabled,
        version: current.version,
      ),
      success: enabled
          ? 'Se habilitó el autorregistro de pacientes.'
          : 'Se deshabilitó el autorregistro de pacientes.',
    );
    if (!mounted) return;
    setState(() {
      _busy = false;
      if (updated != null) _settings = Future.value(updated);
    });
  }

  @override
  Widget build(BuildContext context) {
    return ModulePage(
      title: 'Autorregistro de pacientes',
      body: FutureBuilder<RegistrationSettings>(
        future: _settings,
        builder: (context, snapshot) {
          if (snapshot.hasError) {
            return LoadErrorView(error: snapshot.error!, onRetry: _reload);
          }
          final settings = snapshot.data;
          if (settings == null) return const LoadingView();
          final canConfigure =
              AppScope.of(
                context,
              ).session.session?.can('patients.selfRegistration.configure') ??
              false;
          return ModuleListView(
            children: [
              const Text(
                'Con el autorregistro, una persona adulta puede registrarse como paciente '
                'desde la web o la app con el código de la clínica. Se exigen los mismos '
                'datos y el consentimiento, y debe verificar su correo.',
              ),
              const SizedBox(height: KdConstants.space4),
              SwitchListTile(
                value: settings.selfRegistrationEnabled,
                contentPadding: EdgeInsets.zero,
                onChanged: _busy || !canConfigure
                    ? null
                    : (v) => _toggle(settings, v),
                title: const Text('Permitir el autorregistro de pacientes'),
              ),
              InfoRow('Código de la clínica', settings.publicCode),
            ],
          );
        },
      ),
    );
  }
}
