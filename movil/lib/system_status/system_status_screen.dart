import 'package:flutter/material.dart';

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
                padding: const EdgeInsets.all(24),
                child: ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 480),
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
      child: const Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          CircularProgressIndicator(),
          SizedBox(height: 16),
          ExcludeSemantics(child: Text('Consultando el estado del sistema…')),
        ],
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
    final theme = Theme.of(context);
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
            style: theme.textTheme.headlineSmall,
            textAlign: TextAlign.center,
          ),
        ),
        const SizedBox(height: 24),
        Card(
          child: Column(
            children: [
              _StatusTile(label: 'Aplicación', status: info.status),
              const Divider(height: 1),
              _StatusTile(label: 'Base de datos', status: info.database),
              const Divider(height: 1),
              ListTile(
                title: const Text('Versión'),
                trailing: Text(info.version, style: theme.textTheme.bodyLarge),
              ),
            ],
          ),
        ),
        const SizedBox(height: 24),
        OutlinedButton.icon(
          onPressed: onRefresh,
          icon: const Icon(Icons.refresh),
          label: const Text('Actualizar'),
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
    final scheme = Theme.of(context).colorScheme;
    final up = status == HealthStatus.up;
    final text = up ? 'Disponible' : 'No disponible';
    return MergeSemantics(
      child: ListTile(
        // Icon and text: the status never depends on color alone.
        leading: Icon(
          up ? Icons.check_circle : Icons.error,
          color: up ? scheme.primary : scheme.error,
        ),
        title: Text(label),
        trailing: Text(
          text,
          style: TextStyle(
            color: up ? scheme.onSurface : scheme.error,
            fontWeight: FontWeight.w600,
          ),
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
    final theme = Theme.of(context);
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Icon(Icons.cloud_off, size: 48, color: theme.colorScheme.error),
        const SizedBox(height: 16),
        Semantics(
          liveRegion: true,
          child: Text(
            _message,
            style: theme.textTheme.titleMedium,
            textAlign: TextAlign.center,
          ),
        ),
        const SizedBox(height: 24),
        FilledButton.icon(
          onPressed: onRetry,
          icon: const Icon(Icons.refresh),
          label: const Text('Reintentar'),
        ),
      ],
    );
  }
}
