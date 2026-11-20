import 'package:flutter/material.dart';

import '../design_system/layout.dart';
import '../design_system/tokens.g.dart';
import '../design_system/widgets/widgets.dart';
import 'system_info.dart';
import 'system_info_client.dart';

/// Shows the system status reported by `GET /system/info`.
class SystemStatusScreen extends StatefulWidget {
  const SystemStatusScreen({super.key, required this.client});

  final SystemInfoClient client;

  @override
  State<SystemStatusScreen> createState() => _SystemStatusScreenState();
}

class _SystemStatusScreenState extends State<SystemStatusScreen> {
  late Future<SystemInfo> _future;

  @override
  void initState() {
    super.initState();
    _future = widget.client.fetch();
  }

  void _reload() {
    setState(() {
      _future = widget.client.fetch();
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Estado del sistema')),
      body: SafeArea(
        child: FutureBuilder<SystemInfo>(
          future: _future,
          builder: (context, snapshot) {
            final Widget content;
            if (snapshot.connectionState != ConnectionState.done) {
              content = const _Loading();
            } else if (snapshot.hasError) {
              content = _ErrorView(error: snapshot.error, onRetry: _reload);
            } else {
              content = _InfoView(
                info: snapshot.requireData,
                onRefresh: _reload,
              );
            }
            return Center(
              child: SingleChildScrollView(
                padding: const EdgeInsets.all(KdConstants.space6),
                child: ConstrainedBox(
                  constraints: const BoxConstraints(
                    maxWidth: KdLayout.formMaxWidth,
                  ),
                  child: content,
                ),
              ),
            );
          },
        ),
      ),
    );
  }
}

class _Loading extends StatelessWidget {
  const _Loading();

  @override
  Widget build(BuildContext context) {
    return Semantics(
      liveRegion: true,
      label: 'Consultando el estado del sistema',
      child: const ExcludeSemantics(
        child: KdLoadingState(title: 'Consultando el estado del sistema…'),
      ),
    );
  }
}

class _InfoView extends StatelessWidget {
  const _InfoView({required this.info, required this.onRefresh});

  final SystemInfo info;
  final VoidCallback onRefresh;

  @override
  Widget build(BuildContext context) {
    final allUp =
        info.status == HealthStatus.up && info.database == HealthStatus.up;
    final t = KdTokens.of(context);
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Semantics(
          liveRegion: true,
          header: true,
          child: Text(
            allUp
                ? 'El sistema funciona correctamente'
                : 'El sistema tiene fallas',
            style: t.typeTitle.copyWith(color: t.textDefault),
            textAlign: TextAlign.center,
          ),
        ),
        const SizedBox(height: KdConstants.space6),
        KdListSection(
          children: [
            _StatusTile(label: 'Aplicación', status: info.status),
            _StatusTile(label: 'Base de datos', status: info.database),
            KdListTile(
              leadingIcon: KdIcons.info,
              title: 'Versión',
              trailing: Text(
                info.version,
                style: t.typeBody.copyWith(color: t.textDefault),
              ),
            ),
          ],
        ),
        const SizedBox(height: KdConstants.space6),
        KdButton.secondary(
          label: 'Actualizar',
          icon: KdIcons.refresh,
          expand: true,
          onPressed: onRefresh,
        ),
      ],
    );
  }
}

class _StatusTile extends StatelessWidget {
  const _StatusTile({required this.label, required this.status});

  final String label;
  final HealthStatus status;

  @override
  Widget build(BuildContext context) {
    final t = KdTokens.of(context);
    final up = status == HealthStatus.up;
    final text = up ? 'Disponible' : 'No disponible';
    return MergeSemantics(
      child: KdListTile(
        // Icon and text: the status never depends on color alone (R-08).
        leading: KdIcon(
          up ? KdIcons.circleCheck : KdIcons.circleAlert,
          color: up ? t.colorOk : t.colorError,
        ),
        title: label,
        trailing: Text(
          text,
          style: t.typeLabel.copyWith(color: up ? t.textDefault : t.colorError),
        ),
      ),
    );
  }
}

class _ErrorView extends StatelessWidget {
  const _ErrorView({required this.error, required this.onRetry});

  final Object? error;
  final VoidCallback onRetry;

  String get _message {
    final e = error;
    final reason = e is SystemInfoException ? e.reason : null;
    return switch (reason) {
      SystemInfoFailure.unreachable =>
        'No pudimos conectarnos con el servidor. Revisa tu conexión e inténtalo de nuevo.',
      SystemInfoFailure.serverError =>
        'El servidor no pudo informar su estado. Inténtalo de nuevo en unos minutos.',
      SystemInfoFailure.invalidResponse || null =>
        'Recibimos una respuesta inesperada del servidor. Inténtalo de nuevo.',
    };
  }

  @override
  Widget build(BuildContext context) {
    final t = KdTokens.of(context);
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        KdIcon(
          KdIcons.circleAlert,
          size: KdLayout.statusIconSize,
          color: t.colorError,
        ),
        const SizedBox(height: KdConstants.space4),
        Semantics(
          liveRegion: true,
          child: Text(
            _message,
            style: t.typeSubtitle.copyWith(color: t.textDefault),
            textAlign: TextAlign.center,
          ),
        ),
        const SizedBox(height: KdConstants.space6),
        KdButton(
          label: 'Reintentar',
          icon: KdIcons.refresh,
          expand: true,
          onPressed: onRetry,
        ),
      ],
    );
  }
}
