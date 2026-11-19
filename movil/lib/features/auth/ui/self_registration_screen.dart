import 'package:flutter/material.dart';

import '../../../app_scope.dart';
import '../../../core/api/api_exception.dart';
import '../../../core/connectivity/connectivity_widgets.dart';
import '../../../core/util/client_id.dart';
import '../data/auth_models.dart';
import '../data/registration_catalog.dart';
import 'auth_messages.dart';
import 'auth_widgets.dart';

/// Adult patient self-registration, available only when the clinic enables it
/// (FR-003). Requires a connection; nothing is stored on the phone (FR-029).
class SelfRegistrationScreen extends StatefulWidget {
  const SelfRegistrationScreen({super.key, this.clock});

  /// Injected in tests to make the age check deterministic.
  final DateTime Function()? clock;

  @override
  State<SelfRegistrationScreen> createState() => _SelfRegistrationScreenState();
}

class _SelfRegistrationScreenState extends State<SelfRegistrationScreen> {
  final _formKey = GlobalKey<FormState>();
  final _clinicCode = TextEditingController();
  final _documentNumber = TextEditingController();
  final _firstNames = TextEditingController();
  final _lastNames = TextEditingController();
  final _birthDateText = TextEditingController();
  final _email = TextEditingController();
  final _phone = TextEditingController();
  final _password = TextEditingController();

  /// Same id while this form is open, so a retry is idempotent.
  final String _clientId = newClientId();

  String? _documentType;
  String? _sex;
  DateTime? _birthDate;
  bool _consent = false;
  bool _busy = false;
  bool _done = false;
  String? _error;
  Map<String, String> _fieldErrors = const {};

  DateTime _today() => (widget.clock ?? DateTime.now)();

  @override
  void dispose() {
    for (final c in [
      _clinicCode,
      _documentNumber,
      _firstNames,
      _lastNames,
      _birthDateText,
      _email,
      _phone,
      _password,
    ]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _pickBirthDate() async {
    final today = _today();
    final picked = await showDatePicker(
      context: context,
      initialDate: _birthDate ?? DateTime(today.year - 30, today.month),
      firstDate: DateTime(today.year - 120),
      lastDate: today,
      helpText: 'Fecha de nacimiento',
    );
    if (picked == null) return;
    setState(() {
      _birthDate = picked;
      _birthDateText.text = MaterialLocalizations.of(
        context,
      ).formatMediumDate(picked);
    });
  }

  Future<void> _submit() async {
    setState(() => _fieldErrors = const {});
    if (_busy || !_formKey.currentState!.validate()) return;
    if (!_consent) {
      setState(
        () => _error =
            'Para registrarte debes aceptar el tratamiento de tus datos personales.',
      );
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    final services = AppScope.of(context);
    try {
      await services.authApi.selfRegister(
        publicCode: _clinicCode.text.trim().toLowerCase(),
        registration: SelfRegistration(
          id: _clientId,
          documentType: _documentType!,
          documentNumber: _documentNumber.text.trim(),
          firstNames: _firstNames.text.trim(),
          lastNames: _lastNames.text.trim(),
          birthDate: _birthDate!,
          sex: _sex!,
          email: _email.text.trim(),
          phone: _phone.text.trim(),
          password: _password.text,
          consentTextVersion: services.config.consentTextVersion,
        ),
      );
      if (mounted) setState(() => _done = true);
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _error = authErrorMessage(e);
        _fieldErrors = {
          for (final f in e.fieldErrors)
            _formField(f.field): fieldErrorMessage(f.code),
        };
      });
      _formKey.currentState!.validate();
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  static String _formField(String apiField) =>
      apiField.startsWith('consent') ? 'consent' : apiField;

  FormFieldValidator<String> _validator(
    String field, [
    FormFieldValidator<String>? local,
  ]) {
    return (value) => local?.call(value) ?? _fieldErrors[field];
  }

  String? _birthDateValidator(String? _) {
    final birth = _birthDate;
    if (birth == null) return 'Elige tu fecha de nacimiento.';
    if (ageOn(birth, _today()) < RegistrationCatalog.adultAge) {
      return fieldErrorMessage('MINOR_NOT_ALLOWED');
    }
    return _fieldErrors['birthDate'];
  }

  InputDecoration _decoration(String label, {String? helper}) =>
      InputDecoration(
        labelText: label,
        helperText: helper,
        helperMaxLines: 2,
        border: const OutlineInputBorder(),
      );

  @override
  Widget build(BuildContext context) {
    const title = 'Registro de paciente';
    if (_done) {
      return AuthPage(
        title: title,
        showOfflineBanner: false,
        children: [
          const SuccessMessage(
            'Recibimos tu registro. Te enviamos un correo para verificar tu cuenta; '
            'ábrelo en este teléfono para activarla.',
          ),
          const SizedBox(height: 24),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Volver a iniciar sesión'),
          ),
        ],
      );
    }
    const gap = SizedBox(height: 16);
    return AuthPage(
      title: title,
      children: [
        const Text(
          'Regístrate si tu clínica tiene habilitado el autorregistro. '
          'Los menores de edad se registran en la clínica con su representante.',
        ),
        gap,
        ErrorMessage(_error),
        Form(
          key: _formKey,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              TextFormField(
                controller: _clinicCode,
                autocorrect: false,
                textInputAction: TextInputAction.next,
                validator: _validator('publicCode', (v) {
                  final code = v?.trim().toLowerCase() ?? '';
                  if (code.isEmpty) return 'Ingresa el código de tu clínica.';
                  if (!RegExp(r'^[a-z0-9-]{3,60}$').hasMatch(code)) {
                    return 'Usa solo letras, números y guiones (3 a 60).';
                  }
                  return null;
                }),
                decoration: _decoration(
                  'Código de la clínica',
                  helper: 'Te lo entrega la clínica.',
                ),
              ),
              gap,
              DropdownButtonFormField<String>(
                initialValue: _documentType,
                items: [
                  for (final o in RegistrationCatalog.documentTypes)
                    DropdownMenuItem(value: o.code, child: Text(o.label)),
                ],
                onChanged: (v) => setState(() => _documentType = v),
                validator: (v) => v == null
                    ? 'Elige el tipo de documento.'
                    : _fieldErrors['documentType'],
                decoration: _decoration('Tipo de documento'),
              ),
              gap,
              TextFormField(
                controller: _documentNumber,
                textInputAction: TextInputAction.next,
                validator: _validator('documentNumber', (v) {
                  final t = v?.trim() ?? '';
                  if (t.isEmpty) return 'Este dato es obligatorio.';
                  if (t.length > 30) return 'Usa como máximo 30 caracteres.';
                  return null;
                }),
                decoration: _decoration('Número de documento'),
              ),
              gap,
              TextFormField(
                controller: _firstNames,
                textCapitalization: TextCapitalization.words,
                autofillHints: const [AutofillHints.givenName],
                textInputAction: TextInputAction.next,
                validator: _validator('firstNames', _name),
                decoration: _decoration('Nombres'),
              ),
              gap,
              TextFormField(
                controller: _lastNames,
                textCapitalization: TextCapitalization.words,
                autofillHints: const [AutofillHints.familyName],
                textInputAction: TextInputAction.next,
                validator: _validator('lastNames', _name),
                decoration: _decoration('Apellidos'),
              ),
              gap,
              TextFormField(
                controller: _birthDateText,
                readOnly: true,
                onTap: _pickBirthDate,
                validator: _birthDateValidator,
                decoration: _decoration('Fecha de nacimiento').copyWith(
                  suffixIcon: IconButton(
                    tooltip: 'Elegir fecha de nacimiento',
                    icon: const Icon(Icons.calendar_today),
                    onPressed: _pickBirthDate,
                  ),
                ),
              ),
              gap,
              DropdownButtonFormField<String>(
                initialValue: _sex,
                items: [
                  for (final o in RegistrationCatalog.sexes)
                    DropdownMenuItem(value: o.code, child: Text(o.label)),
                ],
                onChanged: (v) => setState(() => _sex = v),
                validator: (v) =>
                    v == null ? 'Elige el sexo.' : _fieldErrors['sex'],
                decoration: _decoration('Sexo'),
              ),
              gap,
              TextFormField(
                controller: _email,
                keyboardType: TextInputType.emailAddress,
                autocorrect: false,
                autofillHints: const [AutofillHints.email],
                textInputAction: TextInputAction.next,
                validator: _validator('email', emailValidator),
                decoration: _decoration('Correo'),
              ),
              gap,
              TextFormField(
                controller: _phone,
                keyboardType: TextInputType.phone,
                autofillHints: const [AutofillHints.telephoneNumber],
                textInputAction: TextInputAction.next,
                validator: _validator('phone', (v) {
                  final t = v?.trim() ?? '';
                  if (t.isEmpty) return null;
                  if (!RegExp(r'^\+[1-9][0-9]{6,14}$').hasMatch(t)) {
                    return 'Usa el formato internacional, por ejemplo +593…';
                  }
                  return null;
                }),
                decoration: _decoration('Teléfono (opcional)'),
              ),
              gap,
              PasswordField(
                controller: _password,
                label: 'Contraseña',
                helperText: 'Usa al menos $minPasswordLength caracteres.',
                autofillHints: const [AutofillHints.newPassword],
                validator: _validator('password', newPasswordValidator),
              ),
              gap,
              CheckboxListTile(
                value: _consent,
                onChanged: (v) => setState(() => _consent = v ?? false),
                controlAffinity: ListTileControlAffinity.leading,
                contentPadding: EdgeInsets.zero,
                title: const Text(
                  'Acepto el tratamiento de mis datos personales por la clínica.',
                ),
                subtitle: Text(
                  _fieldErrors['consent'] ??
                      'Versión del texto: ${AppScope.of(context).config.consentTextVersion}',
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 24),
        OnlineSubmitButton(
          label: 'Registrarme',
          busy: _busy,
          onPressed: _submit,
        ),
      ],
    );
  }

  static String? _name(String? v) {
    final t = v?.trim() ?? '';
    if (t.isEmpty) return 'Este dato es obligatorio.';
    if (t.length > 120) return 'Usa como máximo 120 caracteres.';
    return null;
  }
}
