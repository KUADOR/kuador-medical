import 'package:flutter/material.dart';

import '../../../app_scope.dart';
import '../../../core/api/api_exception.dart';
import '../../../core/connectivity/connectivity_widgets.dart';
import '../../../core/session/session_models.dart';
import '../data/auth_models.dart';
import 'clinic_selection_screen.dart';

/// Chooses the branch of the session after login (ADR-013).
class BranchSelectionScreen extends StatefulWidget {
  const BranchSelectionScreen({super.key, required this.step});

  final BranchSelectionRequired step;

  @override
  State<BranchSelectionScreen> createState() => _BranchSelectionScreenState();
}

class _BranchSelectionScreenState extends State<BranchSelectionScreen> {
  String? _busyId;
  ApiException? _error;

  Future<void> _select(NamedRef branch) async {
    if (_busyId != null) return;
    final monitor = ConnectivityScope.of(context);
    if (!await monitor.check() || !mounted) return;
    setState(() {
      _busyId = branch.id;
      _error = null;
    });
    final services = AppScope.of(context);
    try {
      final tokens = await services.authApi.selectBranch(
        preAuthToken: widget.step.preAuthToken,
        branchId: branch.id,
      );
      await services.session.signIn(tokens);
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e);
    } finally {
      if (mounted) setState(() => _busyId = null);
    }
  }

  @override
  Widget build(BuildContext context) {
    return SelectionList(
      title: 'Elige la sucursal',
      intro:
          'En ${widget.step.clinic.name} trabajas en varias sucursales. Tus permisos dependen de la sucursal que elijas.',
      items: widget.step.branches,
      icon: Icons.store_mall_directory_outlined,
      busyId: _busyId,
      error: _error,
      onSelect: _select,
    );
  }
}
