import '../api/api_client.dart';
import 'session_models.dart';

/// Session operations of the contract: `/auth/refresh`, `/auth/logout`,
/// `/auth/switch-branch` and `GET /me`.
class SessionApi {
  SessionApi(this._api, {DateTime Function()? clock})
    : _now = clock ?? DateTime.now;

  final ApiClient _api;
  final DateTime Function() _now;

  Future<SessionTokens> refresh(String refreshToken) async {
    final json = await _api.post(
      '/auth/refresh',
      body: {'refreshToken': refreshToken},
    );
    return SessionTokens.fromAuthenticated(json, _now());
  }

  /// Always succeeds on the API side (`204`), even for unknown tokens.
  Future<void> logout(String refreshToken) async {
    await _api.post('/auth/logout', body: {'refreshToken': refreshToken});
  }

  Future<SessionTokens> switchBranch({
    required String branchId,
    required String refreshToken,
  }) async {
    final json = await _api.post(
      '/auth/switch-branch',
      authenticated: true,
      body: {'branchId': branchId, 'refreshToken': refreshToken},
    );
    return SessionTokens.fromAuthenticated(json, _now());
  }

  Future<CurrentSession> me() async =>
      CurrentSession.fromJson(await _api.get('/me'));
}
