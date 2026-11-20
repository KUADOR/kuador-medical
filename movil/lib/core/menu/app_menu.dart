import '../../design_system/widgets/widgets.dart';
import '../session/session_models.dart';

/// Destinations of the main menu. The screens behind them belong to the
/// features of each module (`home_screen.dart`, `moduleScreen`).
enum MenuDestination {
  staffUsers,
  roles,
  patients,
  selfRegistrationSettings,
  myGuardianLinks,
}

class MenuEntry {
  const MenuEntry({
    required this.destination,
    required this.label,
    required this.icon,
  });

  final MenuDestination destination;
  final String label;
  final KdIconData icon;
}

/// Builds the menu from `GET /me`: an entry is shown only when the clinic plan
/// enables its module and the active assignment has its permission (FR-013).
/// The API still checks every operation; the menu only hides what would fail.
List<MenuEntry> buildMenu(CurrentSession session) {
  if (session.mustChangePassword || session.isPlatform) return const [];
  bool allowed(String module, String permission) =>
      session.hasModule(module) && session.can(permission);

  return switch (session.kind) {
    AccountKind.staff => [
      if (allowed('identity', 'identity.users.read'))
        const MenuEntry(
          destination: MenuDestination.staffUsers,
          label: 'Personal',
          icon: KdIcons.stethoscope,
        ),
      if (allowed('identity', 'roles.read'))
        const MenuEntry(
          destination: MenuDestination.roles,
          label: 'Roles y permisos',
          icon: KdIcons.lock,
        ),
      if (allowed('patients', 'patients.read'))
        const MenuEntry(
          destination: MenuDestination.patients,
          label: 'Pacientes',
          icon: KdIcons.heartPulse,
        ),
      if (allowed('patients', 'patients.selfRegistration.configure'))
        const MenuEntry(
          destination: MenuDestination.selfRegistrationSettings,
          label: 'Autorregistro de pacientes',
          icon: KdIcons.userPlus,
        ),
    ],
    AccountKind.patient => [
      if (session.hasModule('patients'))
        const MenuEntry(
          destination: MenuDestination.myGuardianLinks,
          label: 'Mis contactos autorizados',
          icon: KdIcons.users,
        ),
    ],
    AccountKind.guardian => const [],
  };
}
