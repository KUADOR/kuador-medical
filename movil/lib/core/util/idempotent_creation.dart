import 'dart:convert';

import 'client_id.dart';

/// Client id of a creation form, sent both as the body `id` and as the
/// `Idempotency-Key` header (contract: "Idempotent creations").
///
/// - Retrying the same data (for example after a lost connection) reuses the
///   id, so the API returns the original result instead of creating twice.
/// - Changing the data takes a new id, so the API never answers
///   `IDEMPOTENCY_KEY_REUSED` for an honest correction.
/// - [completed] forgets the id once the creation succeeded.
class IdempotentCreation {
  IdempotentCreation({String Function()? newId})
    : _newId = newId ?? newClientId;

  final String Function() _newId;
  String? _id;
  String? _signature;

  /// Id to use for a creation with [payload] (the body without `id`).
  String idFor(Map<String, Object?> payload) {
    final signature = jsonEncode(payload);
    if (_id == null || signature != _signature) {
      _id = _newId();
      _signature = signature;
    }
    return _id!;
  }

  void completed() {
    _id = null;
    _signature = null;
  }
}
