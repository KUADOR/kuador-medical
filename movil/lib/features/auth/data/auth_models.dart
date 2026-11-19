import '../../../core/api/api_client.dart';
import '../../../core/api/api_exception.dart';
import '../../../core/session/session_models.dart';

/// One step of the login flow (`LoginResult` in the contract).
sealed class LoginResult {
  const LoginResult();

  factory LoginResult.fromJson(Object? json, DateTime now) {
    return switch (requireField<String>(json, 'status')) {
      'AUTHENTICATED' => Authenticated(
        SessionTokens.fromAuthenticated(json, now),
      ),
      'CLINIC_SELECTION_REQUIRED' => ClinicSelectionRequired(
        preAuthToken: requireField<String>(json, 'preAuthToken'),
        clinics: NamedRef.listFromJson(
          (json as Map<String, dynamic>)['clinics'],
        ),
      ),
      'BRANCH_SELECTION_REQUIRED' => BranchSelectionRequired(
        preAuthToken: requireField<String>(json, 'preAuthToken'),
        clinic: NamedRef.fromJson((json as Map<String, dynamic>)['clinic']),
        branches: NamedRef.listFromJson(json['branches']),
      ),
      _ => throw const ApiException(code: ApiException.invalidResponseCode),
    };
  }
}

class Authenticated extends LoginResult {
  const Authenticated(this.tokens);

  final SessionTokens tokens;
}

class ClinicSelectionRequired extends LoginResult {
  const ClinicSelectionRequired({
    required this.preAuthToken,
    required this.clinics,
  });

  final String preAuthToken;
  final List<NamedRef> clinics;
}

class BranchSelectionRequired extends LoginResult {
  const BranchSelectionRequired({
    required this.preAuthToken,
    required this.clinic,
    required this.branches,
  });

  final String preAuthToken;
  final NamedRef clinic;
  final List<NamedRef> branches;
}

/// `InvitationPreview` of the contract.
class InvitationPreview {
  const InvitationPreview({
    required this.clinicName,
    required this.maskedEmail,
    required this.kind,
    required this.expiresAt,
  });

  final String clinicName;
  final String maskedEmail;
  final AccountKind kind;
  final DateTime expiresAt;

  factory InvitationPreview.fromJson(Object? json) {
    final expires = DateTime.tryParse(requireField<String>(json, 'expiresAt'));
    if (expires == null) {
      throw const ApiException(code: ApiException.invalidResponseCode);
    }
    return InvitationPreview(
      clinicName: requireField<String>(json, 'clinicName'),
      maskedEmail: requireField<String>(json, 'maskedEmail'),
      kind: AccountKind.parse((json as Map<String, dynamic>)['kind']),
      expiresAt: expires,
    );
  }
}

/// Body of `POST /public/clinics/{publicCode}/patient-registrations`.
class SelfRegistration {
  const SelfRegistration({
    required this.id,
    required this.documentType,
    required this.documentNumber,
    required this.firstNames,
    required this.lastNames,
    required this.birthDate,
    required this.sex,
    required this.email,
    required this.password,
    required this.consentTextVersion,
    this.phone,
    this.address,
  });

  /// Client-generated id, reused when the same form is sent again.
  final String id;
  final String documentType;
  final String documentNumber;
  final String firstNames;
  final String lastNames;
  final DateTime birthDate;
  final String sex;
  final String email;
  final String password;
  final String consentTextVersion;
  final String? phone;
  final String? address;

  Map<String, Object> toJson() => {
    'id': id,
    'documentType': documentType,
    'documentNumber': documentNumber,
    'firstNames': firstNames,
    'lastNames': lastNames,
    'birthDate': _date(birthDate),
    'sex': sex,
    'email': email,
    'password': password,
    'consent': {'textVersion': consentTextVersion},
    if (phone != null && phone!.isNotEmpty) 'phone': phone!,
    if (address != null && address!.isNotEmpty) 'address': address!,
  };

  static String _date(DateTime d) =>
      '${d.year.toString().padLeft(4, '0')}-'
      '${d.month.toString().padLeft(2, '0')}-'
      '${d.day.toString().padLeft(2, '0')}';
}
