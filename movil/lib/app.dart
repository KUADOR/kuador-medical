import 'dart:async';

import 'package:flutter/material.dart';

import 'app_scope.dart';
import 'core/connectivity/connectivity_monitor.dart';
import 'core/connectivity/connectivity_widgets.dart';
import 'core/session/session_controller.dart';
import 'design_system/theme.dart';
import 'design_system/tokens.g.dart';
import 'features/auth/deep_links/deep_link.dart';
import 'features/auth/ui/email_verification_screen.dart';
import 'features/auth/ui/invitation_screen.dart';
import 'features/auth/ui/login_screen.dart';
import 'features/auth/ui/password_reset_confirm_screen.dart';
import 'features/home/home_screen.dart';

class MedicalKuadorApp extends StatefulWidget {
  const MedicalKuadorApp({
    super.key,
    required this.services,
    required this.connectivity,
    required this.deepLinks,
  });

  final AppServices services;
  final ConnectivityMonitor connectivity;
  final DeepLinkSource deepLinks;

  @override
  State<MedicalKuadorApp> createState() => _MedicalKuadorAppState();
}

class _MedicalKuadorAppState extends State<MedicalKuadorApp> {
  final _navigatorKey = GlobalKey<NavigatorState>();
  StreamSubscription<Uri>? _links;
  int? _lastEpoch;

  /// Link received before the stored session was restored.
  DeepLink? _pendingLink;

  SessionController get _session => widget.services.session;

  @override
  void initState() {
    super.initState();
    _session.addListener(_onSessionChanged);
    _links = widget.deepLinks.links.listen(_onLink, onError: (Object _) {});
    unawaited(_session.restore());
  }

  @override
  void dispose() {
    _links?.cancel();
    _session.removeListener(_onSessionChanged);
    super.dispose();
  }

  /// Closes the pushed screens (login steps, deep link flows) whenever a
  /// session starts or ends, so the root shows the right home; then opens a
  /// link that arrived while the session was being restored.
  void _onSessionChanged() {
    if (_session.status == SessionStatus.restoring) return;
    if (_session.epoch != _lastEpoch) {
      _lastEpoch = _session.epoch;
      _navigatorKey.currentState?.popUntil((route) => route.isFirst);
    }
    final pending = _pendingLink;
    if (pending != null) {
      _pendingLink = null;
      WidgetsBinding.instance.addPostFrameCallback((_) => _open(pending));
    }
  }

  void _onLink(Uri uri) {
    final link = parseDeepLink(
      uri,
      allowedHost: widget.services.config.appLinkHost,
    );
    if (link == null) return;
    if (_session.status == SessionStatus.restoring ||
        _navigatorKey.currentState == null) {
      _pendingLink = link;
      return;
    }
    _open(link);
  }

  void _open(DeepLink link) {
    final navigator = _navigatorKey.currentState;
    if (navigator == null) {
      _pendingLink = link;
      return;
    }
    final Widget screen = switch (link) {
      InvitationLink(:final token) => InvitationScreen(token: token),
      PasswordResetLink(:final token) => PasswordResetConfirmScreen(
        token: token,
      ),
      EmailVerificationLink(:final token) => EmailVerificationScreen(
        token: token,
      ),
    };
    // A new link replaces any flow opened by a previous one.
    navigator.popUntil((route) => route.isFirst);
    navigator.push(MaterialPageRoute<void>(builder: (_) => screen));
  }

  @override
  Widget build(BuildContext context) {
    return AppScope(
      services: widget.services,
      child: ConnectivityScope(
        monitor: widget.connectivity,
        child: MaterialApp(
          navigatorKey: _navigatorKey,
          title: 'Medical Kuador',
          debugShowCheckedModeBanner: false,
          theme: KdTheme.light,
          darkTheme: KdTheme.dark,
          themeMode: KdTheme.mode,
          builder: KdTheme.builder,
          home: ListenableBuilder(
            listenable: _session,
            builder: (context, _) => switch (_session.status) {
              SessionStatus.restoring => const _Splash(),
              SessionStatus.signedOut => const LoginScreen(),
              SessionStatus.active => HomeScreen(session: _session.session!),
              SessionStatus.unavailable => const _SessionUnavailable(),
            },
          ),
        ),
      ),
    );
  }
}

class _Splash extends StatelessWidget {
  const _Splash();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: Semantics(
          liveRegion: true,
          label: 'Cargando tu sesión',
          child: const CircularProgressIndicator(),
        ),
      ),
    );
  }
}

class _SessionUnavailable extends StatelessWidget {
  const _SessionUnavailable();

  @override
  Widget build(BuildContext context) {
    final session = AppScope.of(context).session;
    return Scaffold(
      appBar: AppBar(title: const Text('Medical Kuador')),
      body: SafeArea(
        child: Center(
          child: Padding(
            padding: const EdgeInsets.all(KdConstants.space6),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 480),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const OfflineBanner(),
                  const SizedBox(height: 16),
                  Semantics(
                    liveRegion: true,
                    child: const Text(
                      'No pudimos cargar tu sesión. Revisa tu conexión e inténtalo de nuevo.',
                      textAlign: TextAlign.center,
                    ),
                  ),
                  const SizedBox(height: 24),
                  FilledButton(
                    onPressed: session.reload,
                    child: const Text('Reintentar'),
                  ),
                  TextButton(
                    onPressed: session.logout,
                    child: const Text('Cerrar sesión'),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
