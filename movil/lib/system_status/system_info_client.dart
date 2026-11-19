import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import 'system_info.dart';

/// Thrown when the system status cannot be obtained.
class SystemInfoException implements Exception {
  const SystemInfoException(this.reason);

  final SystemInfoFailure reason;

  @override
  String toString() => 'SystemInfoException($reason)';
}

enum SystemInfoFailure {
  /// The API could not be reached or did not answer in time.
  unreachable,

  /// The API answered with a non-200 status.
  serverError,

  /// The API answered 200 with a body that does not match the contract.
  invalidResponse,
}

/// HTTP client for `GET /system/info`.
class SystemInfoClient {
  SystemInfoClient({
    required http.Client httpClient,
    required String baseUrl,
    this.timeout = const Duration(seconds: 10),
  }) : _http = httpClient,
       _baseUrl = baseUrl.endsWith('/')
           ? baseUrl.substring(0, baseUrl.length - 1)
           : baseUrl;

  final http.Client _http;
  final String _baseUrl;
  final Duration timeout;

  Uri get systemInfoUri => Uri.parse('$_baseUrl/system/info');

  Future<SystemInfo> fetch() async {
    final http.Response response;
    try {
      response = await _http
          .get(systemInfoUri, headers: const {'Accept': 'application/json'})
          .timeout(timeout);
    } on TimeoutException {
      throw const SystemInfoException(SystemInfoFailure.unreachable);
    } on http.ClientException {
      throw const SystemInfoException(SystemInfoFailure.unreachable);
    }

    if (response.statusCode != 200) {
      throw const SystemInfoException(SystemInfoFailure.serverError);
    }

    try {
      final body = jsonDecode(utf8.decode(response.bodyBytes));
      if (body is! Map<String, dynamic>) {
        throw const FormatException('Body is not an object');
      }
      return SystemInfo.fromJson(body);
    } on FormatException {
      throw const SystemInfoException(SystemInfoFailure.invalidResponse);
    }
  }
}
