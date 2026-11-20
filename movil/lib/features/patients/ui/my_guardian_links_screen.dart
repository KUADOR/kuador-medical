import 'package:flutter/material.dart';

import '../../../app_scope.dart';
import '../../../core/api/api_exception.dart';
import '../../../core/api/error_messages.dart';
import '../../../core/connectivity/connectivity_widgets.dart';
import '../../../core/ui/module_widgets.dart';
import '../../../core/util/idempotent_creation.dart';
import '../../../design_system/tokens.g.dart';
import '../../../design_system/widgets/widgets.dart';
import '../../auth/ui/auth_widgets.dart';
import '../data/patient_models.dart';
import 'patient_widgets.dart';

/// For an adult patient: their links, including those ended at adulthood,
/// and the explicit authorization of an emergency contact or delegated
/// access (FR-028).
class MyGuardianLinksScreen extends StatefulWidget {
  const MyGuardianLinksScreen({super.key});

  @override
  State<MyGuardianLinksScreen> createState() => _MyGuardianLinksScreenState();
}

class _Person {
  const _Person(this.id, this.name, this.relationship);

  final String id;
  final String name;
  final String relationship;
}

class _MyGuardianLinksScreenState extends State<MyGuardianLinksScreen> {
  static const String _newPerson = '';

  final _formKey = GlobalKey<FormState>();
  final _relationship = TextEditingController();
  final _person = GuardianPersonController();
  final _creation = IdempotentCreation();

  Future<List<GuardianLink>>? _links;
  String? _choice;
  LinkType _linkType = LinkType.emergencyContact;
  bool _busy = false;
  String? _error;
  Map<String, String> _fieldErrors = const {};

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _links ??= AppScope.of(context).patientsApi.myGuardianLinks();
  }

  @override
  void dispose() {
    _relationship.dispose();
    _person.dispose();
    super.dispose();
  }

  void _reload() => setState(() {
    _links = AppScope.of(context).patientsApi.myGuardianLinks();
  });

  /// One option per person of the history, newest link first.
  static List<_Person> _people(List<GuardianLink> links) {
    final seen = <String>{};
    return [
      for (final l in links)
        if (seen.add(l.guardianId))
          _Person(l.guardianId, l.guardianName, l.relationship),
    ];
  }

  Future<void> _submit() async {
    setState(() => _fieldErrors = const {});
    if (_busy || !_formKey.currentState!.validate()) return;
    final newPerson = _choice == _newPerson;
    final person = newPerson ? _person.value() : null;
    final relationship = _relationship.text.trim();
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await AppScope.of(context).patientsApi.authorizeMyLink(
        id: _creation.idFor({
          'linkType': _linkType.code,
          'relationship': relationship,
          'guardianId': newPerson ? null : _choice,
          'person': person?.toJson(),
        }),
        linkType: _linkType,
        relationship: relationship,
        guardianId: newPerson ? null : _choice,
        person: person,
      );
      _creation.completed();
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Autorizaste un ${_linkType.label.toLowerCase()}.'),
        ),
      );
      _relationship.clear();
      _reload();
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
      title: 'Mis contactos autorizados',
      body: FutureBuilder<List<GuardianLink>>(
        future: _links,
        builder: (context, snapshot) {
          if (snapshot.hasError) {
            return LoadErrorView(error: snapshot.error!, onRetry: _reload);
          }
          final links = snapshot.data;
          if (links == null) return const LoadingView();
          final people = _people(links);
          final choice = _choice ??= people.isEmpty
              ? _newPerson
              : people.first.id;
          if (choice != _newPerson && _relationship.text.isEmpty) {
            _relationship.text = people
                .firstWhere(
                  (p) => p.id == choice,
                  orElse: () => const _Person('', '', ''),
                )
                .relationship;
          }
          return ModuleListView(
            children: [
              const Text(
                'Como paciente mayor de edad decides quién puede ser tu contacto de '
                'emergencia o tener acceso delegado. Los vínculos de tu representante '
                'terminaron al cumplir la mayoría de edad y solo continúan si los '
                'autorizas aquí.',
              ),
              const SectionTitle('Mis vínculos'),
              if (links.isEmpty)
                const Text(
                  'No tienes representantes ni contactos registrados.',
                ),
              for (final link in links) GuardianLinkTile(link: link),
              const SectionTitle('Autorizar a una persona'),
              ErrorMessage(_error),
              Form(
                key: _formKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    RadioGroup<String>(
                      groupValue: choice,
                      onChanged: (v) => setState(() {
                        _choice = v;
                        _relationship.text = people
                            .firstWhere(
                              (p) => p.id == v,
                              orElse: () => const _Person('', '', ''),
                            )
                            .relationship;
                      }),
                      child: Column(
                        children: [
                          for (final p in people)
                            RadioListTile<String>(
                              value: p.id,
                              contentPadding: EdgeInsets.zero,
                              title: Text('${p.name} (${p.relationship})'),
                            ),
                          const RadioListTile<String>(
                            value: _newPerson,
                            contentPadding: EdgeInsets.zero,
                            title: Text('Otra persona'),
                          ),
                        ],
                      ),
                    ),
                    if (choice == _newPerson) ...[
                      const SizedBox(height: KdConstants.space2),
                      GuardianPersonFields(
                        controller: _person,
                        errors: nestedErrors(_fieldErrors, 'person'),
                      ),
                    ],
                    const SizedBox(height: KdConstants.space4),
                    Semantics(
                      header: true,
                      child: Text(
                        '¿Qué autorizas?',
                        style: Theme.of(context).textTheme.titleSmall,
                      ),
                    ),
                    RadioGroup<LinkType>(
                      groupValue: _linkType,
                      onChanged: (v) =>
                          setState(() => _linkType = v ?? _linkType),
                      child: const Column(
                        children: [
                          RadioListTile<LinkType>(
                            value: LinkType.emergencyContact,
                            contentPadding: EdgeInsets.zero,
                            title: Text('Contacto de emergencia'),
                          ),
                          RadioListTile<LinkType>(
                            value: LinkType.delegatedAccess,
                            contentPadding: EdgeInsets.zero,
                            title: Text('Acceso delegado'),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: KdConstants.space2),
                    KdTextField(
                      label: 'Parentesco o relación',
                      controller: _relationship,
                      textInputAction: TextInputAction.done,
                      validator: (v) =>
                          FormRules.requiredMax(60)(v) ??
                          _fieldErrors['relationship'],
                    ),
                  ],
                ),
              ),
              const SizedBox(height: KdConstants.space6),
              OnlineSubmitButton(
                label: 'Autorizar',
                busy: _busy,
                onPressed: _submit,
              ),
            ],
          );
        },
      ),
    );
  }
}
