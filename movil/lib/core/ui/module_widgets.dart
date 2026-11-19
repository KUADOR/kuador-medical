import 'package:flutter/material.dart';

import '../api/error_messages.dart';
import '../connectivity/connectivity_widgets.dart';

/// Page of a module screen: app bar, offline banner and a centered, readable
/// width on tablets (mobile-first).
class ModulePage extends StatelessWidget {
  const ModulePage({
    super.key,
    required this.title,
    required this.body,
    this.actions = const [],
    this.floatingActionButton,
  });

  final String title;
  final Widget body;
  final List<Widget> actions;
  final Widget? floatingActionButton;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(title), actions: actions),
      floatingActionButton: floatingActionButton,
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 600),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const Padding(
                  padding: EdgeInsets.fromLTRB(16, 8, 16, 0),
                  child: OfflineBanner(),
                ),
                Expanded(child: body),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Scrollable form or detail body with the standard padding.
class ModuleListView extends StatelessWidget {
  const ModuleListView({super.key, required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 96),
      children: children,
    );
  }
}

/// Loading indicator announced to screen readers.
class LoadingView extends StatelessWidget {
  const LoadingView({super.key});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Semantics(
        liveRegion: true,
        label: 'Cargando',
        child: const CircularProgressIndicator(),
      ),
    );
  }
}

/// Error of a load with a retry button.
class LoadErrorView extends StatelessWidget {
  const LoadErrorView({super.key, required this.error, required this.onRetry});

  final Object error;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Semantics(
              liveRegion: true,
              child: Text(apiErrorMessage(error), textAlign: TextAlign.center),
            ),
            const SizedBox(height: 16),
            FilledButton(
              onPressed: onRetry,
              child: const Text('Volver a intentar'),
            ),
          ],
        ),
      ),
    );
  }
}

/// Section title marked as a header for screen readers.
class SectionTitle extends StatelessWidget {
  const SectionTitle(this.text, {super.key});

  final String text;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(top: 16, bottom: 8),
      child: Semantics(
        header: true,
        child: Text(text, style: Theme.of(context).textTheme.titleMedium),
      ),
    );
  }
}

/// Label and value of a detail screen.
class InfoRow extends StatelessWidget {
  const InfoRow(this.label, this.value, {super.key});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return MergeSemantics(
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(label, style: Theme.of(context).textTheme.labelMedium),
            Text(value, style: Theme.of(context).textTheme.bodyLarge),
          ],
        ),
      ),
    );
  }
}

/// Informational card.
class NoticeCard extends StatelessWidget {
  const NoticeCard(this.text, {super.key});

  final String text;

  @override
  Widget build(BuildContext context) {
    return Card(
      color: Theme.of(context).colorScheme.secondaryContainer,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            const Icon(Icons.info_outline),
            const SizedBox(width: 12),
            Expanded(child: Text(text)),
          ],
        ),
      ),
    );
  }
}

InputDecoration fieldDecoration(String label, {String? helper}) =>
    InputDecoration(
      labelText: label,
      helperText: helper,
      helperMaxLines: 3,
      border: const OutlineInputBorder(),
    );

/// Runs a mutating [action] only with a connection (FR-029): offline, it
/// tells the person and sends nothing. Errors are shown in a snack bar; the
/// result is true when the action finished without error.
Future<bool> runOnline(
  BuildContext context,
  Future<void> Function() action, {
  String? success,
}) async {
  final messenger = ScaffoldMessenger.of(context);
  final online = await ConnectivityScope.of(context).check();
  if (!online) {
    messenger.showSnackBar(const SnackBar(content: Text(offlineMessage)));
    return false;
  }
  try {
    await action();
    if (success != null) {
      messenger.showSnackBar(SnackBar(content: Text(success)));
    }
    return true;
  } on Object catch (e) {
    messenger.showSnackBar(SnackBar(content: Text(apiErrorMessage(e))));
    return false;
  }
}

/// Confirmation dialog; true when the person confirms.
Future<bool> confirmAction(
  BuildContext context, {
  required String title,
  required String message,
  required String confirmLabel,
}) async {
  final result = await showDialog<bool>(
    context: context,
    builder: (context) => AlertDialog(
      title: Text(title),
      content: Text(message),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(false),
          child: const Text('Cancelar'),
        ),
        FilledButton(
          onPressed: () => Navigator.of(context).pop(true),
          child: Text(confirmLabel),
        ),
      ],
    ),
  );
  return result ?? false;
}

String _two(int n) => n.toString().padLeft(2, '0');

/// `dd/mm/aaaa` of a calendar date.
String formatDate(DateTime date) =>
    '${_two(date.day)}/${_two(date.month)}/${date.year}';

/// `dd/mm/aaaa hh:mm` in the device time zone.
String formatDateTime(DateTime instant) {
  final local = instant.toLocal();
  return '${formatDate(local)} ${_two(local.hour)}:${_two(local.minute)}';
}

/// `yyyy-mm-dd` of the contract (`format: date`).
String isoDate(DateTime date) =>
    '${date.year.toString().padLeft(4, '0')}-${_two(date.month)}-${_two(date.day)}';

/// Validators shared by the module forms (limits of the contract).
abstract final class FormRules {
  static String? required(String? v) =>
      (v == null || v.trim().isEmpty) ? 'Este dato es obligatorio.' : null;

  static FormFieldValidator<String> requiredMax(int max) => (v) {
    final t = v?.trim() ?? '';
    if (t.isEmpty) return 'Este dato es obligatorio.';
    if (t.length > max) return 'Usa como máximo $max caracteres.';
    return null;
  };

  static FormFieldValidator<String> optionalMax(int max) => (v) {
    final t = v?.trim() ?? '';
    if (t.length > max) return 'Usa como máximo $max caracteres.';
    return null;
  };

  static String? email(String? v) {
    final t = v?.trim() ?? '';
    if (t.isEmpty) return 'Este dato es obligatorio.';
    return optionalEmail(t);
  }

  static String? optionalEmail(String? v) {
    final t = v?.trim() ?? '';
    if (t.isEmpty) return null;
    if (t.length > 254 || !RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$').hasMatch(t)) {
      return 'Ingresa un correo válido.';
    }
    return null;
  }

  static String? optionalPhone(String? v) {
    final t = v?.trim() ?? '';
    if (t.isEmpty) return null;
    if (!RegExp(r'^\+[1-9][0-9]{6,14}$').hasMatch(t)) {
      return 'Usa el formato internacional, por ejemplo +593…';
    }
    return null;
  }
}
