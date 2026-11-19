import '../../../core/api/api_client.dart';
import '../../../core/session/session_models.dart';
import 'auth_models.dart';

/// Public authentication operations of `contracts/user-registration.openapi.yaml`.
///
/// One-time tokens travel only in request bodies (ADR-014).
class AuthApi {
  AuthApi(this._api, {DateTime Function()? clock})
    : _now = clock ?? DateTime.now;

  final ApiClient _api;
  final DateTime Function() _now;

  Future<LoginResult> login({
    required String email,
    required String password,
  }) async => LoginResult.fromJson(
    await _api.post(
      '/auth/login',
      body: {'email': email.trim(), 'password': password},
    ),
    _now(),
  );

  Future<LoginResult> selectClinic({
    required String preAuthToken,
    required String clinicId,
  }) async => LoginResult.fromJson(
    await _api.post(
      '/auth/login/clinic',
      body: {'preAuthToken': preAuthToken, 'clinicId': clinicId},
    ),
    _now(),
  );

  Future<SessionTokens> selectBranch({
    required String preAuthToken,
    required String branchId,
  }) async => SessionTokens.fromAuthenticated(
    await _api.post(
      '/auth/login/branch',
      body: {'preAuthToken': preAuthToken, 'branchId': branchId},
    ),
    _now(),
  );

  /// Always `202`, whether or not the email exists.
  Future<void> requestPasswordReset(String email) async {
    await _api.post(
      '/auth/password-reset/request',
      body: {'email': email.trim()},
    );
  }

  Future<void> confirmPasswordReset({
    required String token,
    required String newPassword,
  }) async {
    await _api.post(
      '/auth/password-reset/confirm',
      body: {'token': token, 'newPassword': newPassword},
    );
  }

  Future<InvitationPreview> previewInvitation(String token) async =>
      InvitationPreview.fromJson(
        await _api.post('/auth/invitations/preview', body: {'token': token}),
      );

  Future<LoginResult> acceptInvitation({
    required String token,
    required String password,
  }) async => LoginResult.fromJson(
    await _api.post(
      '/auth/invitations/accept',
      body: {'token': token, 'password': password},
    ),
    _now(),
  );

  Future<void> confirmEmailVerification(String token) async {
    await _api.post('/auth/email-verification/confirm', body: {'token': token});
  }

  /// `202` also when the person already exists (the email explains it).
  Future<void> selfRegister({
    required String publicCode,
    required SelfRegistration registration,
  }) async {
    await _api.post(
      '/public/clinics/${Uri.encodeComponent(publicCode)}/patient-registrations',
      headers: {'Idempotency-Key': registration.id},
      body: registration.toJson(),
    );
  }
}
