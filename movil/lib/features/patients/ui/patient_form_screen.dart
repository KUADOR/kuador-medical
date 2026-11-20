import 'package:flutter/material.dart';

import '../../../app_scope.dart';
import '../../../core/api/api_exception.dart';
import '../../../core/api/error_messages.dart';
import '../../../core/connectivity/connectivity_widgets.dart';
import '../../../core/ui/module_widgets.dart';
import '../../../core/util/idempotent_creation.dart';
import '../../../design_system/tokens.g.dart';
import '../../auth/data/registration_catalog.dart';
import '../../auth/ui/auth_widgets.dart';
import '../data/patient_models.dart';
import 'patient_widgets.dart';

/// Registers a patient with the mandatory data and the personal data consent
/// (FR-008, FR-009); a minor needs a legal guardian and gets no account
/// (FR-010). Also edits the administrative data of an existing patient.
/// Requires a connection and nothing is stored on the phone (FR-029).
///
/// Pops the saved [Patient].
class PatientFormScreen extends StatefulWidget {
  const PatientFormScreen({super.key, this.patient});

  /// Null to register a new patient.
  final Patient? patient;

  @override
  State<PatientFormScreen> createState() => _PatientFormScreenState();
}

class _PatientFormScreenState extends State<PatientFormScreen> {
  final _formKey = GlobalKey<FormState>();
  late final _documentNumber = TextEditingController(
    text: widget.patient?.documentNumber ?? '',
  );
  late final _firstNames = TextEditingController(
    text: widget.patient?.firstNames ?? '',
  );
  late final _lastNames = TextEditingController(
    text: widget.patient?.lastNames ?? '',
  );
  late final _email = TextEditingController(text: widget.patient?.email ?? '');
  late final _phone = TextEditingController(text: widget.patient?.phone ?? '');
  late final _address = TextEditingController(
    text: widget.patient?.address ?? '',
  );
  final _relationship = TextEditingController();
  final _guardian = GuardianPersonController();
  final _creation = IdempotentCreation();

  late String? _documentType = widget.patient?.documentType;
  late String? _sex = widget.patient?.sex;
  late DateTime? _birthDate = widget.patient?.birthDate;
  bool _consent = false;
  String _grantedBy = 'PATIENT';
  bool _busy = false;
  String? _error;
  String? _consentError;
  Map<String, String> _fieldErrors = const {};

  bool get _isNew => widget.patient == null;

  @override
  void dispose() {
    for (final c in [
      _documentNumber,
      _firstNames,
      _lastNames,
      _email,
      _phone,
      _address,
      _relationship,
    ]) {
      c.dispose();
    }
    _guardian.dispose();
    super.dispose();
  }

  bool _isMinor(DateTime today) =>
      _birthDate != null &&
      ageOn(_birthDate!, today) < RegistrationCatalog.adultAge;

  PatientRegistration _registration(bool minor, String consentVersion) =>
      PatientRegistration(
        documentType: _documentType!,
        documentNumber: _documentNumber.text.trim(),
        firstNames: _firstNames.text.trim(),
        lastNames: _lastNames.text.trim(),
        birthDate: isoDate(_birthDate!),
        sex: _sex!,
        email: _email.text.trim(),
        phone: _phone.text.trim(),
        address: _address.text.trim(),
        consentTextVersion: consentVersion,
        consentGrantedBy: minor ? 'GUARDIAN' : _grantedBy,
        guardian: minor ? _guardian.value() : null,
        guardianRelationship: minor ? _relationship.text.trim() : null,
      );

  /// Only the fields that changed (partial update).
  Map<String, Object?> _changes() {
    final p = widget.patient!;
    String? optional(TextEditingController c) =>
        c.text.trim().isEmpty ? null : c.text.trim();
    return {
      if (_documentType != p.documentType) 'documentType': _documentType,
      if (_documentNumber.text.trim() != p.documentNumber)
        'documentNumber': _documentNumber.text.trim(),
      if (_firstNames.text.trim() != p.firstNames)
        'firstNames': _firstNames.text.trim(),
      if (_lastNames.text.trim() != p.lastNames)
        'lastNames': _lastNames.text.trim(),
      if (isoDate(_birthDate!) != isoDate(p.birthDate))
        'birthDate': isoDate(_birthDate!),
      if (_sex != p.sex) 'sex': _sex,
      if (optional(_email) != p.email) 'email': optional(_email),
      if (optional(_phone) != p.phone) 'phone': optional(_phone),
      if (optional(_address) != p.address) 'address': optional(_address),
    };
  }

  Future<void> _submit() async {
    final services = AppScope.of(context);
    final minor = _isMinor(services.clock());
    setState(() {
      _fieldErrors = const {};
      _consentError = null;
    });
    final valid = _formKey.currentState!.validate();
    if (_isNew && !_consent) {
      setState(
        () => _consentError =
            'Sin el consentimiento de tratamiento de datos personales no se puede registrar al paciente.',
      );
    }
    if (_busy || !valid || (_isNew && !_consent)) return;
    if (!_isNew && _changes().isEmpty) {
      setState(() => _error = 'No hay cambios para guardar.');
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final Patient saved;
      if (_isNew) {
        final registration = _registration(
          minor,
          services.config.consentTextVersion,
        );
        saved = await services.patientsApi.register(
          id: _creation.idFor(registration.toJson()),
          registration: registration,
        );
        _creation.completed();
      } else {
        saved = await services.patientsApi.update(
          id: widget.patient!.id,
          changes: _changes(),
          version: widget.patient!.version,
        );
      }
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            _isNew
                ? 'Se registró a ${saved.fullName}.'
                : 'Se guardaron los datos del paciente.',
          ),
        ),
      );
      Navigator.of(context).pop(saved);
    } on ApiException catch (e) {
      if (!mounted) return;
      final fields = apiFieldErrors(e);
      setState(() {
        _error = apiErrorMessage(e);
        _fieldErrors = fields;
        final consent = fields.entries
            .where((f) => f.key == 'consent' || f.key.startsWith('consent.'))
            .map((f) => f.value)
            .firstOrNull;
        if (consent != null) _consentError = consent;
        if (fields['guardian'] != null) {
          _error = 'Un paciente menor de edad necesita un representante legal.';
        }
      });
      _formKey.currentState!.validate();
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  FormFieldValidator<String> _v(
    String field,
    FormFieldValidator<String> local,
  ) =>
      (v) => local(v) ?? _fieldErrors[field];

  @override
  Widget build(BuildContext context) {
    final services = AppScope.of(context);
    final today = services.clock();
    final minor = _isMinor(today);
    const gap = SizedBox(height: KdConstants.space4);
    final guardianErrors = nestedErrors(_fieldErrors, 'guardian.person');
    return ModulePage(
      title: _isNew ? 'Nuevo paciente' : 'Editar paciente',
      body: ModuleListView(
        children: [
          if (_isNew)
            const Text(
              'Los datos marcados son obligatorios. La ficha clínica queda vacía y se '
              'completa en otro módulo.',
            ),
          gap,
          ErrorMessage(_error),
          Form(
            key: _formKey,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                CatalogDropdown(
                  label: 'Tipo de documento',
                  options: RegistrationCatalog.documentTypes,
                  value: _documentType,
                  errorText: _fieldErrors['documentType'],
                  onChanged: (v) => setState(() => _documentType = v),
                ),
                gap,
                TextFormField(
                  controller: _documentNumber,
                  validator: _v('documentNumber', FormRules.requiredMax(30)),
                  decoration: fieldDecoration('Número de documento'),
                ),
                gap,
                TextFormField(
                  controller: _firstNames,
                  textCapitalization: TextCapitalization.words,
                  validator: _v('firstNames', FormRules.requiredMax(120)),
                  decoration: fieldDecoration('Nombres'),
                ),
                gap,
                TextFormField(
                  controller: _lastNames,
                  textCapitalization: TextCapitalization.words,
                  validator: _v('lastNames', FormRules.requiredMax(120)),
                  decoration: fieldDecoration('Apellidos'),
                ),
                gap,
                DatePickerField(
                  label: 'Fecha de nacimiento',
                  value: _birthDate,
                  today: today,
                  onChanged: (d) => setState(() => _birthDate = d),
                  validator: (d) => d == null
                      ? 'Elige la fecha de nacimiento.'
                      : _fieldErrors['birthDate'],
                ),
                if (minor) ...[
                  const SizedBox(height: KdConstants.space2),
                  const NoticeCard(
                    'Es menor de edad: se registra con su representante legal y no '
                    'tendrá una cuenta propia mientras sea menor.',
                  ),
                ],
                gap,
                CatalogDropdown(
                  label: 'Sexo',
                  options: RegistrationCatalog.sexes,
                  value: _sex,
                  errorText: _fieldErrors['sex'],
                  onChanged: (v) => setState(() => _sex = v),
                ),
                gap,
                TextFormField(
                  controller: _email,
                  keyboardType: TextInputType.emailAddress,
                  autocorrect: false,
                  validator: _v('email', FormRules.optionalEmail),
                  decoration: fieldDecoration('Correo (opcional)'),
                ),
                gap,
                TextFormField(
                  controller: _phone,
                  keyboardType: TextInputType.phone,
                  validator: _v('phone', FormRules.optionalPhone),
                  decoration: fieldDecoration('Teléfono (opcional)'),
                ),
                gap,
                TextFormField(
                  controller: _address,
                  validator: _v('address', FormRules.optionalMax(250)),
                  decoration: fieldDecoration('Dirección (opcional)'),
                ),
                if (_isNew && minor) ...[
                  const SectionTitle('Representante legal'),
                  TextFormField(
                    controller: _relationship,
                    validator: _v(
                      'guardian.relationship',
                      FormRules.requiredMax(60),
                    ),
                    decoration: fieldDecoration(
                      'Parentesco o relación',
                      helper: 'Por ejemplo, madre, padre o tutor legal.',
                    ),
                  ),
                  gap,
                  GuardianPersonFields(
                    controller: _guardian,
                    errors: guardianErrors,
                  ),
                ],
              ],
            ),
          ),
          if (_isNew) ...[
            const SectionTitle('Consentimiento de datos personales'),
            if (!minor)
              RadioGroup<String>(
                groupValue: _grantedBy,
                onChanged: (v) => setState(() => _grantedBy = v ?? 'PATIENT'),
                child: const Column(
                  children: [
                    RadioListTile<String>(
                      value: 'PATIENT',
                      contentPadding: EdgeInsets.zero,
                      title: Text('Lo acepta el paciente'),
                    ),
                    RadioListTile<String>(
                      value: 'GUARDIAN',
                      contentPadding: EdgeInsets.zero,
                      title: Text('Lo acepta su representante'),
                    ),
                  ],
                ),
              ),
            CheckboxListTile(
              value: _consent,
              onChanged: (v) => setState(() {
                _consent = v ?? false;
                if (_consent) _consentError = null;
              }),
              controlAffinity: ListTileControlAffinity.leading,
              contentPadding: EdgeInsets.zero,
              title: Text(
                minor
                    ? 'El representante acepta el tratamiento de los datos personales del paciente.'
                    : 'Se acepta el tratamiento de los datos personales del paciente.',
              ),
              subtitle: Text(
                'Versión del texto: ${services.config.consentTextVersion}',
              ),
            ),
            ErrorMessage(_consentError),
          ],
          const SizedBox(height: KdConstants.space6),
          OnlineSubmitButton(
            label: _isNew ? 'Registrar paciente' : 'Guardar cambios',
            busy: _busy,
            onPressed: _submit,
          ),
        ],
      ),
    );
  }
}
