import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:medical_kuador/config.dart';
import 'package:medical_kuador/core/session/session_controller.dart';
import 'package:medical_kuador/core/session/token_store.dart';
import 'package:medical_kuador/main.dart';

import '../support/fake_api.dart';

void main() {
  late FakeApi api;
  late MemoryTokenStore store;
  late SessionController session;

  void build({DateTime Function()? clock}) {
    session = buildServices(
      config: const AppConfig(apiBaseUrl: baseUrl),
      httpClient: api.client,
      tokenStore: store,
      clock: clock ?? () => now,
    ).session;
  }

  setUp(() {
    api = FakeApi();
    store = MemoryTokenStore();
  });

  test(
    'restore without stored tokens signs out without calling the API',
    () async {
      build();
      await session.restore();
      expect(session.status, SessionStatus.signedOut);
      expect(api.requests, isEmpty);
    },
  );

  test('restore loads /me with the stored access token', () async {
    store.tokens = storedTokens();
    api.on('GET /me', (_) => json(staffMe()));
    build();

    await session.restore();

    expect(session.status, SessionStatus.active);
    expect(session.session!.displayName, 'Jane Doe');
    expect(api.calls('GET /me').single.bearer, 'stored-access');
  });

  test(
    'refreshes an access token about to expire and stores the rotated tokens',
    () async {
      store.tokens = storedTokens(accessLeft: const Duration(seconds: 10));
      api.on(
        'POST /auth/refresh',
        (_) => json(authenticated(access: 'access-2', refresh: 'refresh-2')),
      );
      api.on('GET /me', (_) => json(staffMe()));
      build();

      await session.restore();

      expect(api.calls('POST /auth/refresh').single.body, {
        'refreshToken': 'stored-refresh',
      });
      expect(api.calls('GET /me').single.bearer, 'access-2');
      expect(store.tokens!.refreshToken, 'refresh-2');
      expect(
        store.tokens!.accessExpiresAt,
        now.add(const Duration(seconds: 900)),
      );
    },
  );

  test(
    'retries once after 401 UNAUTHENTICATED with a refreshed token',
    () async {
      store.tokens = storedTokens();
      var meCalls = 0;
      api.on('GET /me', (r) {
        meCalls++;
        return r.bearer == 'stored-access'
            ? problem('UNAUTHENTICATED', 401)
            : json(staffMe());
      });
      api.on(
        'POST /auth/refresh',
        (_) => json(authenticated(access: 'access-2', refresh: 'refresh-2')),
      );
      build();

      await session.restore();

      expect(meCalls, 2);
      expect(session.status, SessionStatus.active);
    },
  );

  test(
    'concurrent refreshes share one request (rotating refresh token)',
    () async {
      var clock = now;
      api.on(
        'POST /auth/refresh',
        (_) => json(authenticated(access: 'access-2')),
      );
      api.on('GET /me', (_) => json(staffMe()));
      build(clock: () => clock);
      await session.signIn(storedTokens());
      api.requests.clear();
      // The access token (10 minutes) expires while the app is in use.
      clock = now.add(const Duration(minutes: 11));

      final tokens = await Future.wait([
        session.validAccessToken(),
        session.validAccessToken(),
      ]);

      expect(api.calls('POST /auth/refresh'), hasLength(1));
      expect(tokens, ['access-2', 'access-2']);
    },
  );

  test(
    'a rejected refresh token ends the session as expired and clears storage',
    () async {
      store.tokens = storedTokens(accessLeft: Duration.zero);
      api.on(
        'POST /auth/refresh',
        (_) => problem('INVALID_SESSION_TOKEN', 401),
      );
      build();

      await session.restore();

      expect(session.status, SessionStatus.signedOut);
      expect(session.signOutReason, SignOutReason.expired);
      expect(store.tokens, isNull);
    },
  );

  test('a disabled account during a session goes back to the login', () async {
    store.tokens = storedTokens();
    api.on('GET /me', (_) => problem('UNAUTHENTICATED', 401));
    api.on('POST /auth/refresh', (_) => problem('INVALID_SESSION_TOKEN', 401));
    build();

    await session.restore();

    expect(session.status, SessionStatus.signedOut);
    expect(session.signOutReason, SignOutReason.expired);
  });

  test('keeps the tokens when /me is unreachable', () async {
    store.tokens = storedTokens();
    api.on(
      'GET /me',
      (r) => throw http.ClientException('offline', r.request.url),
    );
    build();

    await session.restore();

    expect(session.status, SessionStatus.unavailable);
    expect(store.tokens, isNotNull);
  });

  test('switchBranch sends the refresh token and reloads /me', () async {
    store.tokens = storedTokens();
    var branch = branchMain;
    api.on('GET /me', (_) => json(staffMe(branchId: branch)));
    api.on('POST /auth/switch-branch', (r) {
      branch = branchNorth;
      return json(
        authenticated(access: 'north-access', refresh: 'north-refresh'),
      );
    });
    build();
    await session.restore();

    await session.switchBranch(branchNorth);

    final call = api.calls('POST /auth/switch-branch').single;
    expect(call.bearer, 'stored-access');
    expect(call.body, {
      'branchId': branchNorth,
      'refreshToken': 'stored-refresh',
    });
    expect(session.session!.activeBranch!.id, branchNorth);
    expect(store.tokens!.refreshToken, 'north-refresh');
  });

  test('logout revokes the refresh token and forgets the session', () async {
    store.tokens = storedTokens();
    api.on('GET /me', (_) => json(staffMe()));
    api.on('POST /auth/logout', (_) => json(null, 204));
    build();
    await session.restore();

    await session.logout();

    expect(api.calls('POST /auth/logout').single.body, {
      'refreshToken': 'stored-refresh',
    });
    expect(session.status, SessionStatus.signedOut);
    expect(session.signOutReason, isNull);
    expect(store.tokens, isNull);
  });
}
