import 'package:flutter/material.dart';

import '../../../app_scope.dart';
import '../../../core/ui/module_widgets.dart';
import '../../../core/util/idempotent_creation.dart';
import '../../../design_system/tokens.g.dart';
import '../../auth/data/registration_catalog.dart';
import '../data/patient_models.dart';
import 'add_guardian_screen.dart';
import 'patient_form_screen.dart';
import 'patient_widgets.dart';

/// Administrative data of a patient: consent, account invitation and
/// guardian links (current and historical).
class PatientDetailScreen extends StatefulWidget {
  const PatientDetailScreen({super.key, required this.patientId});

  final String patientId;

  @override
  State<PatientDetailScreen> createState() => _PatientDetailScreenState();
}

class _Data {
  const _Data(this.patient, this.links);

  final Patient patient;
  final List<GuardianLink> links;
}

class _PatientDetailScreenState extends State<PatientDetailScreen> {
  Future<_Data>? _data;
  final _consentCreation = IdempotentCreation();

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _data ??= _load();
  }

  Future<_Data> _load() async {
    final api = AppScope.of(context).patientsApi;
    final results = await Future.wait([
      api.get(widget.patientId),
      api.guardianLinks(widget.patientId),
    ]);
    return _Data(results[0] as Patient, results[1] as List<GuardianLink>);
  }

  void _reload() => setState(() {
    _data = _load();
  });

  Future<void> _edit(Patient patient) async {
    final saved = await Navigator.of(context).push<Patient>(
      MaterialPageRoute(builder: (_) => PatientFormScreen(patient: patient)),
    );
    if (saved != null && mounted) _reload();
  }

  Future<void> _recordConsent(Patient patient) async {
    final services = AppScope.of(context);
    final grantedBy = patient.minor ? 'GUARDIAN' : 'PATIENT';
    final version = services.config.consentTextVersion;
    final confirmed = await confirmAction(
      context,
      title: 'Registrar consentimiento',
      message: patient.minor
          ? 'Confirma que el representante aceptó el tratamiento de los datos personales del paciente (versión $version).'
          : 'Confirma que el paciente aceptó el tratamiento de sus datos personales (versión $version).',
      confirmLabel: 'Registrar',
    );
    if (!confirmed || !mounted) return;
    final id = _consentCreation.idFor({
      'patientId': patient.id,
      'textVersion': version,
      'grantedBy': grantedBy,
    });
    final ok = await runOnline(
      context,
      () => services.patientsApi.recordConsent(
        patientId: patient.id,
        id: id,
        textVersion: version,
        grantedBy: grantedBy,
      ),
      success: 'Se registró el consentimiento de datos personales.',
    );
    if (ok && mounted) {
      _consentCreation.completed();
      _reload();
    }
  }

  Future<void> _invite(Patient patient) async {
    final channel = await showModalBottomSheet<String>(
      context: context,
      showDragHandle: true,
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Semantics(
              header: true,
              child: Text(
                '¿Por dónde enviamos la invitación?',
                style: Theme.of(context).textTheme.titleMedium,
              ),
            ),
            ListTile(
              leading: const Icon(Icons.email_outlined),
              title: const Text('Correo'),
              subtitle: Text(patient.email ?? 'Sin correo registrado'),
              enabled: patient.email != null,
              onTap: () => Navigator.of(context).pop('EMAIL'),
            ),
            ListTile(
              leading: const Icon(Icons.sms_outlined),
              title: const Text('Mensaje de texto'),
              subtitle: Text(patient.phone ?? 'Sin teléfono registrado'),
              enabled: patient.phone != null,
              onTap: () => Navigator.of(context).pop('SMS'),
            ),
          ],
        ),
      ),
    );
    if (channel == null || !mounted) return;
    final api = AppScope.of(context).patientsApi;
    final ok = await runOnline(
      context,
      () => api.inviteAccount(patientId: patient.id, channel: channel),
      success: 'Se envió la invitación para activar la cuenta.',
    );
    if (ok && mounted) _reload();
  }

  Future<void> _endLink(Patient patient, GuardianLink link) async {
    final confirmed = await confirmAction(
      context,
      title: 'Terminar vínculo',
      message:
          '${link.guardianName} dejará de tener acceso como ${link.linkType.label.toLowerCase()}. '
          'El vínculo se conserva como histórico.',
      confirmLabel: 'Terminar',
    );
    if (!confirmed || !mounted) return;
    final api = AppScope.of(context).patientsApi;
    final ok = await runOnline(
      context,
      () => api.endGuardianLink(patientId: patient.id, linkId: link.id),
      success: 'Se terminó el vínculo.',
    );
    if (ok && mounted) _reload();
  }

  Future<void> _addGuardian(Patient patient) async {
    final added = await Navigator.of(context).push<GuardianLink>(
      MaterialPageRoute(builder: (_) => AddGuardianScreen(patient: patient)),
    );
    if (added != null && mounted) _reload();
  }

  @override
  Widget build(BuildContext context) {
    final services = AppScope.of(context);
    final session = services.session.session;
    bool can(String p) => session?.can(p) ?? false;
    return ModulePage(
      title: 'Paciente',
      body: FutureBuilder<_Data>(
        future: _data,
        builder: (context, snapshot) {
          if (snapshot.hasError) {
            return LoadErrorView(error: snapshot.error!, onRetry: _reload);
          }
          final data = snapshot.data;
          if (data == null) return const LoadingView();
          final p = data.patient;
          final consent = p.currentConsent;
          final consentMissing =
              consent == null ||
              consent.revokedAt != null ||
              consent.textVersion != services.config.consentTextVersion;
          return ModuleListView(
            children: [
              Semantics(
                header: true,
                child: Text(
                  p.fullName,
                  style: Theme.of(context).textTheme.headlineSmall,
                ),
              ),
              const SizedBox(height: KdConstants.space2),
              Wrap(
                spacing: KdConstants.space2,
                children: [
                  Chip(label: Text(p.minor ? 'Menor de edad' : 'Adulto')),
                  Chip(label: Text('Cuenta: ${p.accountStatus.label}')),
                ],
              ),
              InfoRow('Documento', p.documentLabel),
              InfoRow('Fecha de nacimiento', formatDate(p.birthDate)),
              InfoRow('Sexo', catalogLabel(RegistrationCatalog.sexes, p.sex)),
              InfoRow('Correo', p.email ?? 'No registrado'),
              InfoRow('Teléfono', p.phone ?? 'No registrado'),
              InfoRow('Dirección', p.address ?? 'No registrada'),
              if (p.adultSince != null)
                InfoRow('Mayor de edad desde', formatDate(p.adultSince!)),
              if (can('patients.edit')) ...[
                const SizedBox(height: KdConstants.space2),
                OutlinedButton.icon(
                  onPressed: () => _edit(p),
                  icon: const Icon(Icons.edit_outlined),
                  label: const Text('Editar datos'),
                ),
              ],
              const SectionTitle('Consentimiento de datos personales'),
              Text(
                consent == null || consent.revokedAt != null
                    ? 'No hay un consentimiento vigente.'
                    : 'Aceptado por ${consent.grantedBy == 'GUARDIAN' ? 'el representante' : 'el paciente'} '
                          'el ${formatDateTime(consent.grantedAt)} (versión ${consent.textVersion}).',
              ),
              if (consentMissing && can('patients.register')) ...[
                const SizedBox(height: KdConstants.space2),
                OutlinedButton.icon(
                  onPressed: () => _recordConsent(p),
                  icon: const Icon(Icons.fact_check_outlined),
                  label: const Text('Registrar consentimiento'),
                ),
              ],
              const SectionTitle('Cuenta del paciente'),
              Text(
                p.minor
                    ? 'Un menor de edad no tiene cuenta propia. Al cumplir 18 años se le ofrecerá activarla.'
                    : 'Estado de la cuenta: ${p.accountStatus.label}.',
              ),
              if (p.canBeInvited && can('patients.register')) ...[
                const SizedBox(height: KdConstants.space2),
                OutlinedButton.icon(
                  onPressed: () => _invite(p),
                  icon: const Icon(Icons.forward_to_inbox_outlined),
                  label: Text(
                    p.accountStatus == PatientAccountStatus.invited
                        ? 'Reenviar invitación'
                        : 'Invitar a activar su cuenta',
                  ),
                ),
              ],
              const SectionTitle('Representantes y contactos'),
              if (data.links.isEmpty)
                const Text('No tiene representantes ni contactos registrados.'),
              for (final link in data.links)
                GuardianLinkTile(
                  link: link,
                  trailing: link.current && can('patients.guardians.manage')
                      ? IconButton(
                          tooltip: 'Terminar vínculo con ${link.guardianName}',
                          icon: const Icon(Icons.link_off),
                          onPressed: () => _endLink(p, link),
                        )
                      : null,
                ),
              if (p.minor && can('patients.guardians.manage')) ...[
                const SizedBox(height: KdConstants.space2),
                OutlinedButton.icon(
                  onPressed: () => _addGuardian(p),
                  icon: const Icon(Icons.person_add_alt_outlined),
                  label: const Text('Agregar representante legal'),
                ),
              ],
              if (!p.minor)
                const Padding(
                  padding: EdgeInsets.only(top: KdConstants.space2),
                  child: Text(
                    'Un paciente adulto autoriza a sus contactos desde su propia cuenta.',
                  ),
                ),
            ],
          );
        },
      ),
    );
  }
}
