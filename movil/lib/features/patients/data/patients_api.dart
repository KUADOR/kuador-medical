import '../../../core/api/api_client.dart';
import '../../../core/api/result_page.dart';
import 'patient_models.dart';

/// Patients, guardians, adult patient links and self-registration settings
/// of `contracts/user-registration.openapi.yaml`.
class PatientsApi {
  PatientsApi(this._api);

  final ApiClient _api;

  static const int pageSize = 20;

  static String _path(String id, [String suffix = '']) =>
      '/patients/${Uri.encodeComponent(id)}$suffix';

  static Map<String, String> _key(String id) => {'Idempotency-Key': id};

  Future<ResultPage<Patient>> list({String query = '', int page = 0}) async =>
      ResultPage.fromJson(
        await _api.get(
          '/patients',
          query: {'q': query.trim(), 'page': '$page', 'size': '$pageSize'},
        ),
        Patient.fromJson,
      );

  Future<Patient> get(String id) async =>
      Patient.fromJson(await _api.get(_path(id)));

  /// [id] is the client id, also sent as `Idempotency-Key`.
  Future<Patient> register({
    required String id,
    required PatientRegistration registration,
  }) async => Patient.fromJson(
    await _api.post(
      '/patients',
      authenticated: true,
      headers: _key(id),
      body: {'id': id, ...registration.toJson()},
    ),
  );

  /// Partial update; [changes] holds only the changed fields.
  Future<Patient> update({
    required String id,
    required Map<String, Object?> changes,
    required int version,
  }) async => Patient.fromJson(
    await _api.patch(_path(id), body: {...changes, 'version': version}),
  );

  Future<void> recordConsent({
    required String patientId,
    required String id,
    required String textVersion,
    required String grantedBy,
  }) async {
    await _api.post(
      _path(patientId, '/consents'),
      authenticated: true,
      headers: _key(id),
      body: {'id': id, 'textVersion': textVersion, 'grantedBy': grantedBy},
    );
  }

  Future<List<GuardianLink>> guardianLinks(String patientId) async =>
      GuardianLink.listFromJson(
        await _api.get(_path(patientId, '/guardian-links')),
      );

  /// Adds a legal guardian (a new person) to a minor.
  Future<GuardianLink> addGuardian({
    required String patientId,
    required String id,
    required GuardianPerson person,
    required String relationship,
  }) async => GuardianLink.fromJson(
    await _api.post(
      _path(patientId, '/guardian-links'),
      authenticated: true,
      headers: _key(id),
      body: {'id': id, 'person': person.toJson(), 'relationship': relationship},
    ),
  );

  Future<GuardianLink> endGuardianLink({
    required String patientId,
    required String linkId,
  }) async => GuardianLink.fromJson(
    await _api.post(
      _path(patientId, '/guardian-links/${Uri.encodeComponent(linkId)}/end'),
      authenticated: true,
    ),
  );

  /// [channel] is `EMAIL` or `SMS`.
  Future<void> inviteAccount({
    required String patientId,
    required String channel,
  }) async {
    await _api.post(
      _path(patientId, '/account-invitation'),
      authenticated: true,
      body: {'channel': channel},
    );
  }

  Future<List<GuardianLink>> myGuardianLinks() async =>
      GuardianLink.listFromJson(await _api.get('/me/guardian-links'));

  /// Authorizes an existing person ([guardianId]) or a new one ([person]).
  Future<GuardianLink> authorizeMyLink({
    required String id,
    required LinkType linkType,
    required String relationship,
    String? guardianId,
    GuardianPerson? person,
  }) async => GuardianLink.fromJson(
    await _api.post(
      '/me/guardian-links',
      authenticated: true,
      headers: _key(id),
      body: {
        'id': id,
        'linkType': linkType.code,
        'relationship': relationship,
        'guardianId': ?guardianId,
        'person': ?person?.toJson(),
      },
    ),
  );

  Future<RegistrationSettings> registrationSettings() async =>
      RegistrationSettings.fromJson(
        await _api.get('/patient-registration-settings'),
      );

  Future<RegistrationSettings> updateRegistrationSettings({
    required bool enabled,
    required int version,
  }) async => RegistrationSettings.fromJson(
    await _api.put(
      '/patient-registration-settings',
      body: {'selfRegistrationEnabled': enabled, 'version': version},
    ),
  );
}
