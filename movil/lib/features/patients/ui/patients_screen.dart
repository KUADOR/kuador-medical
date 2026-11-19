import 'package:flutter/material.dart';

import '../../../app_scope.dart';
import '../../../core/ui/module_widgets.dart';
import '../../../core/ui/paged_search_list.dart';
import '../data/patient_models.dart';
import 'patient_detail_screen.dart';
import 'patient_form_screen.dart';

/// Patients of the clinic (`GET /patients`) with search by name or document.
class PatientsScreen extends StatefulWidget {
  const PatientsScreen({super.key});

  @override
  State<PatientsScreen> createState() => _PatientsScreenState();
}

class _PatientsScreenState extends State<PatientsScreen> {
  int _reloads = 0;

  Future<void> _open(String id) async {
    await Navigator.of(context).push<void>(
      MaterialPageRoute(builder: (_) => PatientDetailScreen(patientId: id)),
    );
    if (mounted) setState(() => _reloads++);
  }

  Future<void> _register() async {
    final created = await Navigator.of(context).push<Patient>(
      MaterialPageRoute(builder: (_) => const PatientFormScreen()),
    );
    if (created != null && mounted) {
      setState(() => _reloads++);
      await _open(created.id);
    }
  }

  @override
  Widget build(BuildContext context) {
    final services = AppScope.of(context);
    final canRegister =
        services.session.session?.can('patients.register') ?? false;
    return ModulePage(
      title: 'Pacientes',
      floatingActionButton: canRegister
          ? FloatingActionButton.extended(
              onPressed: _register,
              icon: const Icon(Icons.person_add_alt_1_outlined),
              label: const Text('Registrar'),
            )
          : null,
      body: PagedSearchList<Patient>(
        reloadKey: _reloads,
        searchLabel: 'Buscar por nombre o documento',
        emptyMessage: 'No hay pacientes que coincidan con la búsqueda.',
        fetch: (query, page) =>
            services.patientsApi.list(query: query, page: page),
        itemBuilder: (context, patient) => Card(
          child: ListTile(
            title: Text(patient.fullName),
            subtitle: Text(
              '${patient.documentLabel} · '
              '${patient.minor ? 'Menor de edad' : formatDate(patient.birthDate)}',
            ),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => _open(patient.id),
          ),
        ),
      ),
    );
  }
}
