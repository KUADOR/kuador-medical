import 'package:flutter/material.dart';

import '../../../app_scope.dart';
import '../../../core/api/api_exception.dart';
import '../../../core/api/error_messages.dart';
import '../../../core/connectivity/connectivity_widgets.dart';
import '../../../core/ui/module_widgets.dart';
import '../../../core/util/idempotent_creation.dart';
import '../../auth/ui/auth_widgets.dart';
import '../data/patient_models.dart';
import 'patient_widgets.dart';

/// Adds a legal guardian to a minor (`POST /patients/{id}/guardian-links`).
/// Pops the new [GuardianLink].
class AddGuardianScreen extends StatefulWidget {
  const AddGuardianScreen({super.key, required this.patient});

  final Patient patient;

  @override
  State<AddGuardianScreen> createState() => _AddGuardianScreenState();
}

class _AddGuardianScreenState extends State<AddGuardianScreen> {
  final _formKey = GlobalKey<FormState>();
  final _relationship = TextEditingController();
  final _person = GuardianPersonController();
  final _creation = IdempotentCreation();
  bool _busy = false;
  String? _error;
  Map<String, String> _fieldErrors = const {};

  @override
  void dispose() {
    _relationship.dispose();
    _person.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    setState(() => _fieldErrors = const {});
    if (_busy || !_formKey.currentState!.validate()) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    final person = _person.value();
    final relationship = _relationship.text.trim();
    try {
      final link = await AppScope.of(context).patientsApi.addGuardian(
        patientId: widget.patient.id,
        id: _creation.idFor({
          'person': person.toJson(),
          'relationship': relationship,
        }),
        person: person,
        relationship: relationship,
      );
      _creation.completed();
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Se agregó el representante legal.')),
      );
      Navigator.of(context).pop(link);
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _error = apiErrorMessage(e);
        _fieldErrors = apiFieldErrors(e);
      });
      _formKey.currentState!.validate();
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return ModulePage(
      title: 'Nuevo representante legal',
      body: ModuleListView(
        children: [
          Text('Representante legal de ${widget.patient.fullName}.'),
          const SizedBox(height: 16),
          ErrorMessage(_error),
          Form(
            key: _formKey,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                TextFormField(
                  controller: _relationship,
                  validator: (v) =>
                      FormRules.requiredMax(60)(v) ??
                      _fieldErrors['relationship'],
                  decoration: fieldDecoration(
                    'Parentesco o relación',
                    helper: 'Por ejemplo, madre, padre o tutor legal.',
                  ),
                ),
                const SizedBox(height: 16),
                GuardianPersonFields(
                  controller: _person,
                  errors: nestedErrors(_fieldErrors, 'person'),
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),
          OnlineSubmitButton(
            label: 'Agregar representante',
            busy: _busy,
            onPressed: _submit,
          ),
        ],
      ),
    );
  }
}
