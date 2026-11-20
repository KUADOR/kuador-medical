import 'package:flutter/material.dart';

import '../../design_system/tokens.g.dart';
import 'connectivity_monitor.dart';

/// Makes the [ConnectivityMonitor] available to the widget tree.
class ConnectivityScope extends InheritedNotifier<ConnectivityMonitor> {
  const ConnectivityScope({
    super.key,
    required ConnectivityMonitor monitor,
    required super.child,
  }) : super(notifier: monitor);

  static ConnectivityMonitor of(BuildContext context) {
    final scope = context
        .dependOnInheritedWidgetOfExactType<ConnectivityScope>();
    assert(scope != null, 'No ConnectivityScope above this widget');
    return scope!.notifier!;
  }
}

/// Message shown when a form cannot be sent without a connection.
const String offlineMessage =
    'Sin conexión. Conéctate a internet para enviar; no se guarda nada en el teléfono.';

/// Banner shown above forms while the device is offline.
class OfflineBanner extends StatelessWidget {
  const OfflineBanner({super.key});

  @override
  Widget build(BuildContext context) {
    final online = ConnectivityScope.of(context).isOnline;
    if (online) return const SizedBox.shrink();
    final scheme = Theme.of(context).colorScheme;
    return Semantics(
      liveRegion: true,
      container: true,
      child: Material(
        color: scheme.errorContainer,
        borderRadius: BorderRadius.circular(KdConstants.radiusLg),
        child: Padding(
          padding: const EdgeInsets.all(KdConstants.space3),
          child: Row(
            children: [
              Icon(Icons.wifi_off, color: scheme.onErrorContainer),
              const SizedBox(width: 12),
              Expanded(
                child: Text(
                  offlineMessage,
                  style: TextStyle(color: scheme.onErrorContainer),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Primary submit button that is disabled while offline or busy.
///
/// [onPressed] runs only after a fresh connectivity check, so a form is never
/// sent, nor queued, without a connection.
class OnlineSubmitButton extends StatelessWidget {
  const OnlineSubmitButton({
    super.key,
    required this.label,
    required this.onPressed,
    this.busy = false,
  });

  final String label;
  final Future<void> Function() onPressed;
  final bool busy;

  @override
  Widget build(BuildContext context) {
    final monitor = ConnectivityScope.of(context);
    final enabled = monitor.isOnline && !busy;
    return FilledButton(
      onPressed: enabled
          ? () async {
              if (await monitor.check()) await onPressed();
            }
          : null,
      style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(48)),
      child: busy
          ? Semantics(
              label: 'Enviando',
              child: const SizedBox.square(
                dimension: 20,
                child: CircularProgressIndicator(strokeWidth: 2),
              ),
            )
          : Text(label),
    );
  }
}
