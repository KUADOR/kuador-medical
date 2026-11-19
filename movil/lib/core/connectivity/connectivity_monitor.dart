import 'dart:async';

import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:flutter/foundation.dart';

/// Tells whether the device has a network connection.
///
/// In this epic every registration requires a connection (FR-029): forms ask
/// [isOnline] before sending and nothing is stored locally to send later.
abstract class ConnectivityMonitor extends ChangeNotifier {
  bool get isOnline;

  /// Checks again (for example right before sending a form).
  Future<bool> check();
}

/// Uses the platform network state (`connectivity_plus`).
///
/// A network interface does not guarantee that the API is reachable; requests
/// still report `UNREACHABLE`, which forms show as a connection error.
class PlatformConnectivityMonitor extends ConnectivityMonitor {
  PlatformConnectivityMonitor([Connectivity? connectivity])
    : _connectivity = connectivity ?? Connectivity() {
    _subscription = _connectivity.onConnectivityChanged.listen(
      _update,
      onError: (Object _) {},
    );
    unawaited(check());
  }

  final Connectivity _connectivity;
  late final StreamSubscription<List<ConnectivityResult>> _subscription;
  bool _online = true;

  @override
  bool get isOnline => _online;

  @override
  Future<bool> check() async {
    try {
      _update(await _connectivity.checkConnectivity());
    } on Object {
      // Unknown state: keep the last known value.
    }
    return _online;
  }

  void _update(List<ConnectivityResult> results) {
    final online = results.any((r) => r != ConnectivityResult.none);
    if (online == _online) return;
    _online = online;
    notifyListeners();
  }

  @override
  void dispose() {
    _subscription.cancel();
    super.dispose();
  }
}

/// Manually controlled monitor for tests and previews.
class FakeConnectivityMonitor extends ConnectivityMonitor {
  FakeConnectivityMonitor({bool online = true}) : _online = online;

  bool _online;

  @override
  bool get isOnline => _online;

  set online(bool value) {
    if (value == _online) return;
    _online = value;
    notifyListeners();
  }

  @override
  Future<bool> check() async => _online;
}
