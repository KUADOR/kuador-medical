import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import 'api_exception.dart';

/// Supplies a valid access token for authenticated requests and refreshes it
/// when the API rejects it. Implemented by the session layer.
abstract interface class AccessTokenProvider {
  /// A non-expired access token, or null when there is no session.
  Future<String?> validAccessToken();

  /// Called after a `401 UNAUTHENTICATED`. Returns a new access token, or null
  /// when the session cannot be recovered (the caller then gives up).
  Future<String?> refreshAfterRejection(String rejectedToken);
}

/// Thin JSON client for the `/api/v1` contract.
///
/// - Public operations are sent without credentials.
/// - Authenticated operations carry `Authorization: Bearer` and are retried
///   once after a token refresh when the API answers `401 UNAUTHENTICATED`.
/// - Non-2xx answers become [ApiException] with the Problem Details `code`.
class ApiClient {
  ApiClient({
    required http.Client httpClient,
    required String baseUrl,
    this.timeout = const Duration(seconds: 15),
  }) : _http = httpClient,
       baseUrl = baseUrl.endsWith('/')
           ? baseUrl.substring(0, baseUrl.length - 1)
           : baseUrl;

  final http.Client _http;
  final String baseUrl;
  final Duration timeout;
  AccessTokenProvider? _tokens;

  /// Wires the session after construction (the session also uses this client).
  set accessTokenProvider(AccessTokenProvider provider) => _tokens = provider;

  Uri uri(String path) => Uri.parse('$baseUrl$path');

  Future<Object?> get(String path, {bool authenticated = true}) =>
      _send('GET', path, authenticated: authenticated);

  Future<Object?> post(
    String path, {
    Object? body,
    bool authenticated = false,
    Map<String, String> headers = const {},
  }) => _send(
    'POST',
    path,
    body: body,
    authenticated: authenticated,
    headers: headers,
  );

  Future<Object?> _send(
    String method,
    String path, {
    Object? body,
    required bool authenticated,
    Map<String, String> headers = const {},
  }) async {
    String? token;
    if (authenticated) {
      token = await _tokens?.validAccessToken();
      if (token == null) {
        throw const ApiException(code: 'UNAUTHENTICATED', status: 401);
      }
    }
    var response = await _raw(method, path, body, token, headers);
    if (authenticated &&
        response.statusCode == 401 &&
        _problemCode(response) == 'UNAUTHENTICATED') {
      final renewed = await _tokens?.refreshAfterRejection(token!);
      if (renewed != null) {
        response = await _raw(method, path, body, renewed, headers);
      }
    }
    return _decode(response);
  }

  Future<http.Response> _raw(
    String method,
    String path,
    Object? body,
    String? token,
    Map<String, String> extraHeaders,
  ) async {
    final request = http.Request(method, uri(path));
    request.headers['Accept'] = 'application/json, application/problem+json';
    if (token != null) request.headers['Authorization'] = 'Bearer $token';
    request.headers.addAll(extraHeaders);
    if (body != null) {
      request.headers['Content-Type'] = 'application/json';
      request.body = jsonEncode(body);
    }
    try {
      final streamed = await _http.send(request).timeout(timeout);
      return await http.Response.fromStream(streamed).timeout(timeout);
    } on TimeoutException {
      throw const ApiException.unreachable();
    } on http.ClientException {
      throw const ApiException.unreachable();
    }
  }

  Object? _decode(http.Response response) {
    final status = response.statusCode;
    if (status >= 200 && status < 300) {
      if (response.bodyBytes.isEmpty) return null;
      try {
        return jsonDecode(utf8.decode(response.bodyBytes));
      } on FormatException {
        throw ApiException(
          code: ApiException.invalidResponseCode,
          status: status,
        );
      }
    }
    final problem = _problem(response);
    final retryAfter = int.tryParse(response.headers['retry-after'] ?? '');
    throw ApiException(
      code: problem?['code'] is String
          ? problem!['code'] as String
          : (status >= 500
                ? 'INTERNAL_ERROR'
                : ApiException.invalidResponseCode),
      status: status,
      fieldErrors: _fieldErrors(problem),
      retryAfter: retryAfter == null ? null : Duration(seconds: retryAfter),
    );
  }

  static Map<String, dynamic>? _problem(http.Response response) {
    try {
      final body = jsonDecode(utf8.decode(response.bodyBytes));
      return body is Map<String, dynamic> ? body : null;
    } on FormatException {
      return null;
    }
  }

  static String? _problemCode(http.Response response) {
    final code = _problem(response)?['code'];
    return code is String ? code : null;
  }

  static List<FieldError> _fieldErrors(Map<String, dynamic>? problem) {
    final errors = problem?['errors'];
    if (errors is! List) return const [];
    return [
      for (final e in errors)
        if (e is Map && e['field'] is String && e['code'] is String)
          FieldError(field: e['field'] as String, code: e['code'] as String),
    ];
  }
}

/// Reads a required field of a JSON object, or throws an invalid-response error.
T requireField<T>(Object? json, String name) {
  if (json is Map<String, dynamic>) {
    final value = json[name];
    if (value is T) return value;
  }
  throw const ApiException(code: ApiException.invalidResponseCode);
}

/// Reads an optional field of a JSON object (null when missing or null).
T? optionalField<T>(Map<String, dynamic> json, String name) {
  final value = json[name];
  if (value == null) return null;
  if (value is T) return value;
  throw const ApiException(code: ApiException.invalidResponseCode);
}
