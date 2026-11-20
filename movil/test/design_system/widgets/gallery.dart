import 'package:flutter/material.dart';
import 'package:medical_kuador/design_system/tokens.g.dart';
import 'package:medical_kuador/design_system/widgets/widgets.dart';

/// Synthetic screens built only with the widget library, for the goldens and
/// the accessibility checks. Fictitious data only (FR-014).

/// Buttons, fields and the bottom navigation.
class ControlsGallery extends StatelessWidget {
  const ControlsGallery({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Agendar cita')),
      body: KdFormScrollView(
        children: [
          const KdTextField(
            label: 'Correo',
            hint: 'nombre@ejemplo.test',
            prefixIcon: KdIcons.mail,
            keyboardType: TextInputType.emailAddress,
          ),
          const KdTextField(
            label: 'Contraseña',
            obscureText: true,
            helperText: 'Al menos 8 caracteres.',
          ),
          const KdTextField(
            label: 'Teléfono',
            errorText: 'Escribe un teléfono de 10 dígitos.',
            textInputAction: TextInputAction.done,
          ),
          KdButton(
            label: 'Confirmar cita',
            icon: KdIcons.calendarCheck,
            expand: true,
            onPressed: () {},
          ),
          KdButton.secondary(label: 'Cambiar fecha', onPressed: () {}),
          Wrap(
            children: [
              KdButton.text(label: 'Ver detalles', onPressed: () {}),
              const KdButton.secondary(label: 'Sin horario', onPressed: null),
            ],
          ),
        ],
      ),
      bottomNavigationBar: KdBottomNavBar(
        selectedIndex: 1,
        onSelected: (_) {},
        destinations: const [
          KdNavDestination(icon: KdIcons.house, label: 'Inicio'),
          KdNavDestination(icon: KdIcons.calendar, label: 'Citas'),
          KdNavDestination(icon: KdIcons.fileText, label: 'Resultados'),
          KdNavDestination(icon: KdIcons.user, label: 'Perfil'),
        ],
      ),
    );
  }
}

/// List rows and cards.
class ListGallery extends StatelessWidget {
  const ListGallery({super.key});

  @override
  Widget build(BuildContext context) {
    final t = KdTokens.of(context);
    return Scaffold(
      appBar: AppBar(title: const Text('Mis citas')),
      body: ListView(
        padding: EdgeInsets.all(t.space4),
        children: [
          KdCard(
            onTap: () {},
            semanticLabel: 'Próxima cita',
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Próxima cita', style: t.typeTitle),
                SizedBox(height: t.space1),
                Text(
                  'Medicina general · Sucursal Centro',
                  style: t.typeBodySm.copyWith(color: t.textMuted),
                ),
              ],
            ),
          ),
          SizedBox(height: t.space4),
          KdListSection(
            title: 'Especialidades',
            children: [
              KdListTile(
                title: 'Medicina general',
                subtitle: 'Dra. Ana Ejemplo',
                leadingIcon: KdIcons.stethoscope,
                onTap: () {},
              ),
              KdListTile(
                title: 'Odontología',
                subtitle: 'Dr. Luis Ficticio',
                leadingIcon: KdIcons.heartPulse,
                selected: true,
                onTap: () {},
              ),
              const KdListTile(
                title: 'Farmacia',
                subtitle: 'Sin turnos hoy',
                leadingIcon: KdIcons.pill,
              ),
            ],
          ),
          SizedBox(height: t.space4),
          const KdCard(
            selected: true,
            child: Text('Lunes 7 · 09:30 · Disponible'),
          ),
        ],
      ),
    );
  }
}

/// Empty state with an action.
class EmptyGallery extends StatelessWidget {
  const EmptyGallery({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: KdEmptyState(
        icon: KdIcons.calendar,
        title: 'Aún no tienes citas',
        message: 'Agenda tu primera cita en pocos pasos.',
        action: KdButton(
          label: 'Agendar cita',
          icon: KdIcons.calendarPlus,
          onPressed: () {},
        ),
      ),
    );
  }
}

/// Error state with retry.
class ErrorGallery extends StatelessWidget {
  const ErrorGallery({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(body: KdErrorState(onRetry: () {}));
  }
}

/// Loading state.
class LoadingGallery extends StatelessWidget {
  const LoadingGallery({super.key});

  @override
  Widget build(BuildContext context) {
    return const Scaffold(body: KdLoadingState(message: 'Buscando horarios.'));
  }
}

/// A screen that opens [overlay] once its first frame is drawn.
class OverlayGallery extends StatefulWidget {
  const OverlayGallery.sheet({super.key}) : dialog = false;
  const OverlayGallery.dialog({super.key}) : dialog = true;

  final bool dialog;

  @override
  State<OverlayGallery> createState() => _OverlayGalleryState();
}

class _OverlayGalleryState extends State<OverlayGallery> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      if (widget.dialog) {
        showKdDialog<void>(context: context, builder: (_) => sampleDialog());
      } else {
        showKdBottomSheet<void>(
          context: context,
          builder: (_) => sampleSheet(),
        );
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return const Scaffold(body: KdEmptyState(title: 'Fondo'));
  }
}

KdSheet sampleSheet() => KdSheet(
  title: 'Elige un horario',
  actions: [
    KdButton(label: 'Confirmar horario', onPressed: () {}),
    KdButton.secondary(label: 'Ver más fechas', onPressed: () {}),
  ],
  child: const KdTextField(label: 'Motivo de la consulta'),
);

KdDialog sampleDialog() => KdDialog(
  title: '¿Cancelar la cita?',
  message: 'Liberaremos el horario para otro paciente.',
  confirm: KdButton.destructive(label: 'Cancelar cita', onPressed: () {}),
  dismiss: KdButton.text(label: 'Volver', onPressed: () {}),
);
