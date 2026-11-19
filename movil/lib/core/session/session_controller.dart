import 'dart:async';

import 'package:flutter/foundation.dart';

import '../api/api_client.dart';
import '../api/api_exception.dart';
import 'session_api.dart';
import 'session_models.dart';
import 'token_store.dart';

enum SessionStatus {
  /// Reading the stored session at startup.
  restoring,

  /// No session: the login is shown.
  signedOut,

  /// Signed in and `/me` loaded.
  active,

  /// There are tokens but `/me` could not be loaded (for example, no network).
  unavailable,
}

/// Why the app went back to the login without the person asking for it.
enum SignOutReason { expired }

/// Owns the tokens and the current session (`/me`).
///
/// - Tokens live only in [TokenStore] and in memory.
/// - The access token is refreshed shortly before it expires, and once more
///   when the API rejects it; concurrent refreshes share one request because
///   the refresh token rotates.
/// - A refresh token the API rejects ends the session (expired, revoked or a
///   disabled account).
class SessionController extends ChangeNotifier implements AccessTokenProvider {
  SessionController({
    required SessionApi api,
    required TokenStore store,
    DateTime Function()? clock,
  }) : _api = api,
       _store = store,
       _now = clock ?? DateTime.now;

  final SessionApi _api;
  final TokenStore _store;
  final DateTime Function() _now;

  SessionStatus _status = SessionStatus.restoring;
  CurrentSession? _session;
  SessionTokens? _tokens;
  SignOutReason? _signOutReason;
  Future<SessionTokens?>? _refreshing;
  int _epoch = 0;

  SessionStatus get status => _status;

  /// Changes each time a session starts or ends (not on branch switches or
  /// reloads), so the UI can close the screens of the previous session.
  int get epoch => _epoch;

  /// Loaded `/me`; non-null only when [status] is [SessionStatus.active].
  CurrentSession? get session => _session;

  /// Set when the session ended on its own; cleared on the next sign-in.
  SignOutReason? get signOutReason => _signOutReason;

  /// Reads the stored session and loads `/me`.
  Future<void> restore() async {
    _tokens = await _store.read();
    final tokens = _tokens;
    if (tokens == null || tokens.refreshExpired(_now())) {
      await _clear(reason: null);
      return;
    }
    await reload();
  }

  /// Stores the tokens of a completed login and loads `/me`.
  Future<void> signIn(SessionTokens tokens) async {
    final previous = _tokens;
    _tokens = tokens;
    _signOutReason = null;
    _epoch++;
    await _store.write(tokens);
    if (previous != null && previous.refreshToken != tokens.refreshToken) {
      unawaited(_revokeQuietly(previous.refreshToken));
    }
    await reload();
  }

  /// Loads `/me` again (after a retry, a branch switch or a role change).
  Future<void> reload() async {
    if (_tokens == null) {
      await _clear(reason: null);
      return;
    }
    try {
      _session = await _api.me();
      _setStatus(SessionStatus.active);
    } on ApiException catch (e) {
      if (_tokens == null) return; // The refresh already ended the session.
      if (e.status == 401) {
        await _clear(reason: SignOutReason.expired);
      } else {
        _session = null;
        _setStatus(SessionStatus.unavailable);
      }
    }
  }

  /// Issues tokens for another branch (`POST /auth/switch-branch`) and reloads
  /// `/me`. Throws [ApiException] (for example `BRANCH_NOT_ASSIGNED`).
  Future<void> switchBranch(String branchId) async {
    final current = _tokens;
    if (current == null) {
      throw const ApiException(code: 'UNAUTHENTICATED', status: 401);
    }
    final tokens = await _api.switchBranch(
      branchId: branchId,
      refreshToken: current.refreshToken,
    );
    _tokens = tokens;
    await _store.write(tokens);
    await reload();
  }

  /// Revokes the refresh token (best effort) and forgets the session.
  Future<void> logout() async {
    final tokens = _tokens;
    await _clear(reason: null);
    if (tokens != null) await _revokeQuietly(tokens.refreshToken);
  }

  @override
  Future<String?> validAccessToken() async {
    final tokens = _tokens;
    if (tokens == null) return null;
    if (!tokens.accessExpiresSoon(_now())) return tokens.accessToken;
    return (await _refresh())?.accessToken;
  }

  @override
  Future<String?> refreshAfterRejection(String rejectedToken) async {
    final tokens = _tokens;
    if (tokens == null) return null;
    if (tokens.accessToken != rejectedToken) return tokens.accessToken;
    return (await _refresh())?.accessToken;
  }

  Future<SessionTokens?> _refresh() {
    return _refreshing ??= _doRefresh().whenComplete(() => _refreshing = null);
  }

  Future<SessionTokens?> _doRefresh() async {
    final tokens = _tokens;
    if (tokens == null) return null;
    if (tokens.refreshExpired(_now())) {
      await _clear(reason: SignOutReason.expired);
      return null;
    }
    try {
      final renewed = await _api.refresh(tokens.refreshToken);
      _tokens = renewed;
      await _store.write(renewed);
      return renewed;
    } on ApiException catch (e) {
      if (e.isUnreachable || (e.status ?? 0) >= 500) rethrow;
      await _clear(reason: SignOutReason.expired);
      return null;
    }
  }

  Future<void> _revokeQuietly(String refreshToken) async {
    try {
      await _api.logout(refreshToken);
    } on ApiException {
      // The token expires on its own; nothing to tell the person.
    }
  }

  Future<void> _clear({required SignOutReason? reason}) async {
    _tokens = null;
    _session = null;
    _signOutReason = reason;
    _epoch++;
    await _store.clear();
    _setStatus(SessionStatus.signedOut, force: true);
  }

  void _setStatus(SessionStatus status, {bool force = false}) {
    if (_status == status && !force && status != SessionStatus.active) return;
    _status = status;
    notifyListeners();
  }
}
