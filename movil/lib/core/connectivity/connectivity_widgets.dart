import 'package:flutter/material.dart';

import '../../design_system/widgets/widgets.dart';
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

/// Notice shown above forms while the device is offline.
class OfflineBanner extends StatelessWidget {
  const OfflineBanner({super.key});

  @override
  Widget build(BuildContext context) {
    final online = ConnectivityScope.of(context).isOnline;
    if (online) return const SizedBox.shrink();
    return const KdInlineMessage.warning(offlineMessage);
  }
}

/// Main (primary) action of a form, full width, disabled while offline and
/// showing progress while [busy] (DESIGN.md, R-02).
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
    return KdButton(
      label: label,
      expand: true,
      loading: busy,
      onPressed: monitor.isOnline
          ? () async {
              if (await monitor.check()) await onPressed();
            }
          : null,
    );
  }
}
