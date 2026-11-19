import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import 'session_models.dart';

/// Persists the session tokens. Tokens never go to logs or plain preferences.
abstract interface class TokenStore {
  Future<SessionTokens?> read();

  Future<void> write(SessionTokens tokens);

  Future<void> clear();
}

/// Keychain (iOS) / Keystore-backed storage (Android).
class SecureTokenStore implements TokenStore {
  SecureTokenStore([FlutterSecureStorage? storage])
    : _storage =
          storage ??
          const FlutterSecureStorage(
            iOptions: IOSOptions(
              // Not restored on another device and not readable before the
              // first unlock after a reboot.
              accessibility: KeychainAccessibility.first_unlock_this_device,
            ),
          );

  static const String _key = 'medical_kuador.session.v1';

  final FlutterSecureStorage _storage;

  @override
  Future<SessionTokens?> read() async {
    final String? raw;
    try {
      raw = await _storage.read(key: _key);
    } on Object {
      // Unreadable keychain entry (for example after a restore): start over.
      await clear();
      return null;
    }
    if (raw == null) return null;
    try {
      final json = jsonDecode(raw);
      if (json is Map<String, dynamic>) return SessionTokens.fromStorage(json);
    } on FormatException {
      // Corrupt value: treated as no session.
    }
    await clear();
    return null;
  }

  @override
  Future<void> write(SessionTokens tokens) =>
      _storage.write(key: _key, value: jsonEncode(tokens.toStorage()));

  @override
  Future<void> clear() async {
    try {
      await _storage.delete(key: _key);
    } on Object {
      // Nothing else to do: the next read treats it as no session.
    }
  }
}

/// In-memory store for tests and previews.
class MemoryTokenStore implements TokenStore {
  MemoryTokenStore([this.tokens]);

  SessionTokens? tokens;

  @override
  Future<SessionTokens?> read() async => tokens;

  @override
  Future<void> write(SessionTokens tokens) async => this.tokens = tokens;

  @override
  Future<void> clear() async => tokens = null;
}
