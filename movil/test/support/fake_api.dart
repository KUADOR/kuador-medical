import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:medical_kuador/app.dart';
import 'package:medical_kuador/app_scope.dart';
import 'package:medical_kuador/config.dart';
import 'package:medical_kuador/core/connectivity/connectivity_monitor.dart';
import 'package:medical_kuador/core/session/session_models.dart';
import 'package:medical_kuador/core/session/token_store.dart';
import 'package:medical_kuador/features/auth/deep_links/deep_link.dart';
import 'package:medical_kuador/main.dart';

const baseUrl = 'https://api.example.test/api/v1';
const linkHost = 'links.example.test';

/// Fixed clock of the tests.
final DateTime now = DateTime.utc(2030, 1, 1, 12);

http.Response json(Object? body, [int status = 200]) => http.Response(
  body == null ? '' : jsonEncode(body),
  status,
  headers: {'content-type': 'application/json'},
);

http.Response problem(
  String code,
  int status, {
  List<Map<String, String>> errors = const [],
  Map<String, String> headers = const {},
}) => http.Response(
  jsonEncode({
    'type': 'about:blank',
    'title': 'Error',
    'status': status,
    'code': code,
    if (errors.isNotEmpty) 'errors': errors,
  }),
  status,
  headers: {'content-type': 'application/problem+json', ...headers},
);

/// `AuthenticatedResult` of the contract with distinguishable tokens.
Map<String, Object?> authenticated({
  String access = 'access-1',
  String refresh = 'refresh-1',
  int expiresIn = 900,
}) => {
  'status': 'AUTHENTICATED',
  'accessToken': access,
  'tokenType': 'Bearer',
  'expiresIn': expiresIn,
  'refreshToken': refresh,
  'refreshExpiresIn': 604800,
  'scope': 'TENANT',
  'clinic': {'id': clinicA, 'name': 'Example Clinic'},
  'branch': {'id': branchMain, 'name': 'Main branch'},
  'mustChangePassword': false,
};

const clinicA = '5b0c8f2e-3a51-4d6e-9f0a-1c2b3d4e5f60';
const clinicB = '9d8c7b6a-5f4e-4d3c-8b2a-1f0e9d8c7b6a';
const branchMain = '2f6d1c0a-8b3e-4a7d-9c5f-6e1b2a3c4d5e';
const branchNorth = '8e7d6c5b-4a39-4281-9f0e-d1c2b3a49586';

/// `CurrentSession` of the contract (staff example).
Map<String, Object?> staffMe({
  String branchId = branchMain,
  String branchName = 'Main branch',
  List<String> permissions = const [
    'patients.read',
    'patients.register',
    'patients.edit',
    'patients.guardians.manage',
  ],
  List<String> modules = const ['identity', 'patients'],
  List<String> roles = const ['Reception'],
  bool mustChangePassword = false,
}) => {
  'accountId': '0f1e2d3c-4b5a-4968-8776-655443322110',
  'kind': 'STAFF',
  'scope': 'TENANT',
  'displayName': 'Jane Doe',
  'clinic': {'id': clinicA, 'name': 'Example Clinic'},
  'activeBranch': {'id': branchId, 'name': branchName},
  'availableBranches': [
    {'id': branchMain, 'name': 'Main branch'},
    {'id': branchNorth, 'name': 'North branch'},
  ],
  'roles': roles,
  'permissions': permissions,
  'enabledModules': modules,
  'mustChangePassword': mustChangePassword,
};

Map<String, Object?> patientMe() => {
  'accountId': '3c2b1a09-8f7e-4d6c-9b5a-4f3e2d1c0b9a',
  'kind': 'PATIENT',
  'scope': 'TENANT',
  'displayName': 'John Roe',
  'clinic': {'id': clinicA, 'name': 'Example Clinic'},
  'activeBranch': null,
  'availableBranches': <Object>[],
  'patientId': '6a5b4c3d-2e1f-4a0b-9c8d-7e6f5a4b3c2d',
  'roles': <String>[],
  'permissions': <String>[],
  'enabledModules': ['patients'],
  'mustChangePassword': false,
};

SessionTokens storedTokens({
  String access = 'stored-access',
  String refresh = 'stored-refresh',
  Duration accessLeft = const Duration(minutes: 10),
}) => SessionTokens(
  accessToken: access,
  accessExpiresAt: now.add(accessLeft),
  refreshToken: refresh,
  refreshExpiresAt: now.add(const Duration(days: 7)),
);

/// Request recorded by [FakeApi], with its decoded JSON body.
class Recorded {
  Recorded(this.request)
    : body = request.body.isEmpty ? null : jsonDecode(request.body);

  final http.Request request;
  final Object? body;

  String get path => request.url.path.replaceFirst(Uri.parse(baseUrl).path, '');

  String? get bearer {
    final h = request.headers['Authorization'];
    return h?.startsWith('Bearer ') == true ? h!.substring(7) : null;
  }
}

typedef Handler = http.Response Function(Recorded request);

/// Contract mock: answers by `METHOD /path` and records every request.
class FakeApi {
  final Map<String, Handler> routes = {};
  final List<Recorded> requests = [];

  void on(String route, Handler handler) => routes[route] = handler;

  List<Recorded> calls(String route) => [
    for (final r in requests)
      if ('${r.request.method} ${r.path}' == route) r,
  ];

  late final MockClient client = MockClient((request) async {
    final recorded = Recorded(request);
    requests.add(recorded);
    final handler = routes['${request.method} ${recorded.path}'];
    if (handler == null) return problem('NOT_FOUND', 404);
    return handler(recorded);
  });
}

class TestApp {
  TestApp({
    required this.api,
    required this.store,
    required this.connectivity,
    required this.links,
    required this.services,
  });

  final FakeApi api;
  final MemoryTokenStore store;
  final FakeConnectivityMonitor connectivity;
  final FakeDeepLinkSource links;
  final AppServices services;
}

Future<TestApp> pumpApp(
  WidgetTester tester, {
  FakeApi? api,
  SessionTokens? stored,
  bool online = true,
  void Function(FakeDeepLinkSource links)? beforeRestore,
}) async {
  final fakeApi = api ?? FakeApi();
  final store = MemoryTokenStore(stored);
  final connectivity = FakeConnectivityMonitor(online: online);
  final links = FakeDeepLinkSource();
  final services = buildServices(
    config: const AppConfig(apiBaseUrl: baseUrl, appLinkHost: linkHost),
    httpClient: fakeApi.client,
    tokenStore: store,
    clock: () => now,
  );
  await tester.pumpWidget(
    MedicalKuadorApp(
      services: services,
      connectivity: connectivity,
      deepLinks: links,
    ),
  );
  beforeRestore?.call(links);
  await tester.pumpAndSettle();
  return TestApp(
    api: fakeApi,
    store: store,
    connectivity: connectivity,
    links: links,
    services: services,
  );
}

Future<void> enterText(WidgetTester tester, String label, String text) async {
  await tester.enterText(find.widgetWithText(TextFormField, label), text);
}

Future<void> tapText(WidgetTester tester, String text) async {
  final finder = find.text(text);
  await tester.ensureVisible(finder.last);
  await tester.tap(finder.last);
  await tester.pumpAndSettle();
}

Finder button(String label) => find.ancestor(
  of: find.text(label),
  matching: find.byWidgetPredicate((w) => w is ButtonStyleButton),
);
