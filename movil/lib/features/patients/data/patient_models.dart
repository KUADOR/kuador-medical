import '../../../core/api/api_client.dart';
import '../../../core/api/api_exception.dart';
import '../../auth/data/registration_catalog.dart';

DateTime _date(Map<String, dynamic> map, String name) {
  final value = DateTime.tryParse(requireField<String>(map, name));
  if (value == null) {
    throw const ApiException(code: ApiException.invalidResponseCode);
  }
  return value;
}

DateTime? _optionalDate(Map<String, dynamic> map, String name) {
  final raw = optionalField<String>(map, name);
  return raw == null ? null : DateTime.tryParse(raw);
}

Map<String, dynamic> _map(Object? json) => json is Map<String, dynamic>
    ? json
    : throw const ApiException(code: ApiException.invalidResponseCode);

/// Spanish label of a catalog code, or the code itself when unknown.
String catalogLabel(List<CatalogOption> options, String code) => options
    .firstWhere((o) => o.code == code, orElse: () => CatalogOption(code, code))
    .label;

/// `Consent` of the contract.
class Consent {
  const Consent({
    required this.textVersion,
    required this.grantedAt,
    required this.grantedBy,
    this.revokedAt,
  });

  final String textVersion;
  final DateTime grantedAt;

  /// `PATIENT` or `GUARDIAN`.
  final String grantedBy;
  final DateTime? revokedAt;

  factory Consent.fromJson(Object? json) {
    final map = _map(json);
    return Consent(
      textVersion: requireField<String>(map, 'textVersion'),
      grantedAt: _date(map, 'grantedAt'),
      grantedBy: requireField<String>(map, 'grantedBy'),
      revokedAt: _optionalDate(map, 'revokedAt'),
    );
  }
}

/// `PatientAccountStatus` of the contract, with its Spanish label.
enum PatientAccountStatus {
  none('NONE', 'Sin cuenta'),
  invited('INVITED', 'Invitación enviada'),
  pendingEmailVerification(
    'PENDING_EMAIL_VERIFICATION',
    'Pendiente de verificar el correo',
  ),
  active('ACTIVE', 'Activa'),
  disabled('DISABLED', 'Desactivada');

  const PatientAccountStatus(this.code, this.label);

  final String code;
  final String label;

  static PatientAccountStatus parse(Object? value) =>
      PatientAccountStatus.values.firstWhere(
        (s) => s.code == value,
        orElse: () =>
            throw const ApiException(code: ApiException.invalidResponseCode),
      );
}

/// `Patient` of the contract.
class Patient {
  const Patient({
    required this.id,
    required this.documentType,
    required this.documentNumber,
    required this.firstNames,
    required this.lastNames,
    required this.birthDate,
    required this.sex,
    required this.minor,
    required this.origin,
    required this.accountStatus,
    required this.version,
    this.email,
    this.phone,
    this.address,
    this.adultSince,
    this.currentConsent,
  });

  final String id;
  final String documentType;
  final String documentNumber;
  final String firstNames;
  final String lastNames;
  final DateTime birthDate;
  final String sex;
  final String? email;
  final String? phone;
  final String? address;

  /// True while the patient is under 18 (decided by the API with the clinic
  /// time zone).
  final bool minor;
  final DateTime? adultSince;

  /// `STAFF` or `SELF`.
  final String origin;
  final PatientAccountStatus accountStatus;
  final Consent? currentConsent;
  final int version;

  String get fullName => '$firstNames $lastNames'.trim();

  String get documentLabel =>
      '${catalogLabel(RegistrationCatalog.documentTypes, documentType)} $documentNumber';

  /// An adult without an active account can be invited to activate one.
  bool get canBeInvited =>
      !minor &&
      (accountStatus == PatientAccountStatus.none ||
          accountStatus == PatientAccountStatus.invited);

  factory Patient.fromJson(Object? json) {
    final map = _map(json);
    final consent = map['currentConsent'];
    return Patient(
      id: requireField<String>(map, 'id'),
      documentType: requireField<String>(map, 'documentType'),
      documentNumber: requireField<String>(map, 'documentNumber'),
      firstNames: requireField<String>(map, 'firstNames'),
      lastNames: requireField<String>(map, 'lastNames'),
      birthDate: _date(map, 'birthDate'),
      sex: requireField<String>(map, 'sex'),
      email: optionalField<String>(map, 'email'),
      phone: optionalField<String>(map, 'phone'),
      address: optionalField<String>(map, 'address'),
      minor: requireField<bool>(map, 'minor'),
      adultSince: _optionalDate(map, 'adultSince'),
      origin: requireField<String>(map, 'origin'),
      accountStatus: PatientAccountStatus.parse(map['accountStatus']),
      currentConsent: consent == null ? null : Consent.fromJson(consent),
      version: requireField<int>(map, 'version'),
    );
  }
}

/// `LinkType` of the contract, with its Spanish label.
enum LinkType {
  legalGuardian('LEGAL_GUARDIAN', 'Representante legal'),
  emergencyContact('EMERGENCY_CONTACT', 'Contacto de emergencia'),
  delegatedAccess('DELEGATED_ACCESS', 'Acceso delegado');

  const LinkType(this.code, this.label);

  final String code;
  final String label;

  static LinkType parse(Object? value) => LinkType.values.firstWhere(
    (t) => t.code == value,
    orElse: () =>
        throw const ApiException(code: ApiException.invalidResponseCode),
  );
}

/// `GuardianLink` of the contract.
class GuardianLink {
  const GuardianLink({
    required this.id,
    required this.guardianId,
    required this.guardianName,
    required this.linkType,
    required this.relationship,
    required this.validFrom,
    required this.current,
    this.validTo,
    this.endReason,
  });

  final String id;
  final String guardianId;
  final String guardianName;
  final LinkType linkType;
  final String relationship;
  final DateTime validFrom;
  final DateTime? validTo;

  /// `ADULTHOOD` or `MANUAL`.
  final String? endReason;
  final bool current;

  String? get endReasonLabel => switch (endReason) {
    'ADULTHOOD' => 'Mayoría de edad del paciente',
    'MANUAL' => 'Terminado por la clínica',
    _ => null,
  };

  factory GuardianLink.fromJson(Object? json) {
    final map = _map(json);
    final guardian = _map(map['guardian']);
    return GuardianLink(
      id: requireField<String>(map, 'id'),
      guardianId: requireField<String>(guardian, 'id'),
      guardianName:
          '${requireField<String>(guardian, 'firstNames')} '
                  '${requireField<String>(guardian, 'lastNames')}'
              .trim(),
      linkType: LinkType.parse(map['linkType']),
      relationship: requireField<String>(map, 'relationship'),
      validFrom: _date(map, 'validFrom'),
      validTo: _optionalDate(map, 'validTo'),
      endReason: optionalField<String>(map, 'endReason'),
      current: requireField<bool>(map, 'current'),
    );
  }

  static List<GuardianLink> listFromJson(Object? json) {
    if (json is! List) {
      throw const ApiException(code: ApiException.invalidResponseCode);
    }
    return [for (final item in json) GuardianLink.fromJson(item)];
  }
}

/// `GuardianPersonInput` of the contract.
class GuardianPerson {
  const GuardianPerson({
    required this.documentType,
    required this.documentNumber,
    required this.firstNames,
    required this.lastNames,
    this.email,
    this.phone,
  });

  final String documentType;
  final String documentNumber;
  final String firstNames;
  final String lastNames;
  final String? email;
  final String? phone;

  Map<String, Object?> toJson() => {
    'documentType': documentType,
    'documentNumber': documentNumber,
    'firstNames': firstNames,
    'lastNames': lastNames,
    if (email != null && email!.isNotEmpty) 'email': email,
    if (phone != null && phone!.isNotEmpty) 'phone': phone,
  };
}

/// Body of `POST /patients` (without `id`).
class PatientRegistration {
  const PatientRegistration({
    required this.documentType,
    required this.documentNumber,
    required this.firstNames,
    required this.lastNames,
    required this.birthDate,
    required this.sex,
    required this.consentTextVersion,
    required this.consentGrantedBy,
    this.email,
    this.phone,
    this.address,
    this.guardian,
    this.guardianRelationship,
  });

  final String documentType;
  final String documentNumber;
  final String firstNames;
  final String lastNames;

  /// `yyyy-mm-dd`.
  final String birthDate;
  final String sex;
  final String? email;
  final String? phone;
  final String? address;
  final String consentTextVersion;

  /// `PATIENT` or `GUARDIAN` (always `GUARDIAN` for a minor).
  final String consentGrantedBy;

  /// Required for minors.
  final GuardianPerson? guardian;
  final String? guardianRelationship;

  Map<String, Object?> toJson() => {
    'documentType': documentType,
    'documentNumber': documentNumber,
    'firstNames': firstNames,
    'lastNames': lastNames,
    'birthDate': birthDate,
    'sex': sex,
    if (email != null && email!.isNotEmpty) 'email': email,
    if (phone != null && phone!.isNotEmpty) 'phone': phone,
    if (address != null && address!.isNotEmpty) 'address': address,
    'consent': {
      'textVersion': consentTextVersion,
      'grantedBy': consentGrantedBy,
    },
    if (guardian != null)
      'guardian': {
        'relationship': guardianRelationship,
        'person': guardian!.toJson(),
      },
  };
}

/// `RegistrationSettings` of the contract.
class RegistrationSettings {
  const RegistrationSettings({
    required this.selfRegistrationEnabled,
    required this.publicCode,
    required this.version,
  });

  final bool selfRegistrationEnabled;
  final String publicCode;
  final int version;

  factory RegistrationSettings.fromJson(Object? json) => RegistrationSettings(
    selfRegistrationEnabled: requireField<bool>(
      json,
      'selfRegistrationEnabled',
    ),
    publicCode: requireField<String>(json, 'publicCode'),
    version: requireField<int>(json, 'version'),
  );
}
