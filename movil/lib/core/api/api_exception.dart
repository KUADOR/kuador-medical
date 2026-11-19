/// Error returned by the API or by the transport, mapped from Problem Details
/// (RFC 9457) with the stable `code` of the contract (`ErrorCode`).
class ApiException implements Exception {
  const ApiException({
    required this.code,
    this.status,
    this.fieldErrors = const [],
    this.retryAfter,
  });

  /// Transport failure: no network, DNS error or timeout.
  const ApiException.unreachable()
    : code = unreachableCode,
      status = null,
      fieldErrors = const [],
      retryAfter = null;

  /// Pseudo-code used when the API could not be reached.
  static const String unreachableCode = 'UNREACHABLE';

  /// Pseudo-code used when the response does not match the contract.
  static const String invalidResponseCode = 'INVALID_RESPONSE';

  /// Value of `code` in the Problem Details, or a pseudo-code above.
  final String code;

  /// HTTP status, or null for transport failures.
  final int? status;

  /// Field-level validation errors (only for `VALIDATION`).
  final List<FieldError> fieldErrors;

  /// Value of the `Retry-After` header, when present.
  final Duration? retryAfter;

  bool get isUnreachable => code == unreachableCode;

  /// Never includes request or response bodies: they may hold tokens or
  /// personal data.
  @override
  String toString() => 'ApiException($code, status: $status)';
}

/// One entry of `errors` in a `VALIDATION` problem.
class FieldError {
  const FieldError({required this.field, required this.code});

  final String field;
  final String code;
}
