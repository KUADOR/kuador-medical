import 'package:flutter/material.dart';

import '../../../app_scope.dart';
import '../../../core/api/api_exception.dart';
import '../../../core/connectivity/connectivity_widgets.dart';
import '../../../core/util/client_id.dart';
import '../../../design_system/tokens.g.dart';
import '../../../design_system/widgets/widgets.dart';
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

  @override
  Widget build(BuildContext context) {
    const title = 'Registro de paciente';
    if (_done) {
      return AuthPage(
        title: title,
        showOfflineBanner: false,
        primaryAction: KdButton(
          label: 'Volver a iniciar sesión',
          expand: true,
          onPressed: () => Navigator.of(context).pop(),
        ),
        children: const [
          SuccessMessage(
            'Recibimos tu registro. Te enviamos un correo para verificar tu cuenta; '
            'ábrelo en este teléfono para activarla.',
            title: 'Revisa tu correo',
          ),
        ],
      );
    }
    final t = KdTokens.of(context);
    final gap = SizedBox(height: t.space4);
    final consentError = _fieldErrors['consent'];
    return AuthPage(
      title: title,
      primaryAction: OnlineSubmitButton(
        label: 'Registrarme',
        busy: _busy,
        onPressed: _submit,
      ),
      children: [
        const AuthHeader(
          title: 'Crea tu cuenta de paciente',
          message:
              'Regístrate si tu clínica tiene habilitado el autorregistro. '
              'Los menores de edad se registran en la clínica con su representante.',
        ),
        if (_error != null) KdInlineMessage.error(_error!),
        Form(
          key: _formKey,
          child: AutofillGroup(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const _SectionTitle('Tu clínica'),
                KdTextField(
                  label: 'Código de la clínica',
                  controller: _clinicCode,
                  prefixIcon: KdIcons.building,
                  helperText: 'Te lo entrega la clínica.',
                  validator: _validator('publicCode', (v) {
                    final code = v?.trim().toLowerCase() ?? '';
                    if (code.isEmpty) return 'Ingresa el código de tu clínica.';
                    if (!RegExp(r'^[a-z0-9-]{3,60}$').hasMatch(code)) {
                      return 'Usa solo letras, números y guiones (3 a 60).';
                    }
                    return null;
                  }),
                ),
                const _SectionTitle('Tus datos'),
                KdSelectField<String>(
                  label: 'Tipo de documento',
                  value: _documentType,
                  options: [
                    for (final o in RegistrationCatalog.documentTypes)
                      KdSelectOption(o.code, o.label),
                  ],
                  onChanged: (v) => setState(() => _documentType = v),
                  validator: (v) => v == null
                      ? 'Elige el tipo de documento.'
                      : _fieldErrors['documentType'],
                ),
                gap,
                KdTextField(
                  label: 'Número de documento',
                  controller: _documentNumber,
                  validator: _validator('documentNumber', (v) {
                    final text = v?.trim() ?? '';
                    if (text.isEmpty) return 'Este dato es obligatorio.';
                    if (text.length > 30) {
                      return 'Usa como máximo 30 caracteres.';
                    }
                    return null;
                  }),
                ),
                gap,
                KdTextField(
                  label: 'Nombres',
                  controller: _firstNames,
                  textCapitalization: TextCapitalization.words,
                  autofillHints: const [AutofillHints.givenName],
                  validator: _validator('firstNames', _name),
                ),
                gap,
                KdTextField(
                  label: 'Apellidos',
                  controller: _lastNames,
                  textCapitalization: TextCapitalization.words,
                  autofillHints: const [AutofillHints.familyName],
                  validator: _validator('lastNames', _name),
                ),
                gap,
                KdTextField(
                  label: 'Fecha de nacimiento',
                  controller: _birthDateText,
                  readOnly: true,
                  onTap: _pickBirthDate,
                  suffixIcon: KdIcons.calendar,
                  validator: _birthDateValidator,
                ),
                gap,
                KdSelectField<String>(
                  label: 'Sexo',
                  value: _sex,
                  options: [
                    for (final o in RegistrationCatalog.sexes)
                      KdSelectOption(o.code, o.label),
                  ],
                  onChanged: (v) => setState(() => _sex = v),
                  validator: (v) =>
                      v == null ? 'Elige el sexo.' : _fieldErrors['sex'],
                ),
                const _SectionTitle('Tu cuenta'),
                KdTextField(
                  label: 'Correo',
                  controller: _email,
                  prefixIcon: KdIcons.mail,
                  keyboardType: TextInputType.emailAddress,
                  autofillHints: const [AutofillHints.email],
                  validator: _validator('email', emailValidator),
                ),
                gap,
                KdTextField(
                  label: 'Teléfono (opcional)',
                  controller: _phone,
                  prefixIcon: KdIcons.phone,
                  keyboardType: TextInputType.phone,
                  autofillHints: const [AutofillHints.telephoneNumber],
                  validator: _validator('phone', (v) {
                    final text = v?.trim() ?? '';
                    if (text.isEmpty) return null;
                    if (!RegExp(r'^\+[1-9][0-9]{6,14}$').hasMatch(text)) {
                      return 'Usa el formato internacional, por ejemplo +593…';
                    }
                    return null;
                  }),
                ),
                gap,
                KdTextField(
                  label: 'Contraseña',
                  controller: _password,
                  obscureText: true,
                  helperText: 'Usa al menos $minPasswordLength caracteres.',
                  autofillHints: const [AutofillHints.newPassword],
                  textInputAction: TextInputAction.done,
                  validator: _validator('password', newPasswordValidator),
                ),
                gap,
                KdCard(
                  padding: EdgeInsets.zero,
                  child: CheckboxListTile(
                    value: _consent,
                    onChanged: (v) => setState(() => _consent = v ?? false),
                    controlAffinity: ListTileControlAffinity.leading,
                    contentPadding: EdgeInsets.symmetric(horizontal: t.space2),
                    title: Text(
                      'Acepto el tratamiento de mis datos personales por la clínica.',
                      style: t.typeBody.copyWith(color: t.textDefault),
                    ),
                    subtitle: Text(
                      consentError ??
                          'Versión del texto: ${AppScope.of(context).config.consentTextVersion}',
                      style: t.typeBodySm.copyWith(
                        color: consentError == null
                            ? t.textMuted
                            : t.colorError,
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
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

/// Heading of a group of fields, announced as a header.
class _SectionTitle extends StatelessWidget {
  const _SectionTitle(this.text);

  final String text;

  @override
  Widget build(BuildContext context) {
    final t = KdTokens.of(context);
    return Padding(
      padding: EdgeInsets.only(top: t.space2, bottom: t.space3),
      child: Semantics(
        header: true,
        child: Text(text, style: t.typeSubtitle.copyWith(color: t.textDefault)),
      ),
    );
  }
}
