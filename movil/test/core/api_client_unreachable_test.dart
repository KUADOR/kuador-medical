// Uses a real socket, so it must not run under the widget test binding
// (which answers every HTTP request with 400).
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:medical_kuador/core/api/api_client.dart';
import 'package:medical_kuador/core/api/api_exception.dart';
import 'package:medical_kuador/core/config/api_config.dart';

void main() {
  test('a refused connection to the api is reported as unreachable', () async {
    // Reserve a free local port and close it so nothing is listening there.
    final socket = await ServerSocket.bind(InternetAddress.loopbackIPv4, 0);
    final port = socket.port;
    await socket.close();

    final httpClient = http.Client();
    addTearDown(httpClient.close);
    final api = ApiClient(
      httpClient: httpClient,
      baseUrl: ApiConfig.parse('http://127.0.0.1:$port').apiV1Url,
      timeout: const Duration(seconds: 5),
    );

    await expectLater(
      api.post(
        '/auth/login',
        body: {'email': 'jane.doe@example.com', 'password': 'example'},
      ),
      throwsA(
        isA<ApiException>().having((e) => e.isUnreachable, 'unreachable', true),
      ),
    );
  });
}
