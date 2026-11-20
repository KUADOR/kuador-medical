import 'package:flutter/material.dart';

import '../tokens.g.dart';
import 'kd_icon.dart';
import 'kd_style.dart';

/// One destination of the [KdBottomNavBar].
@immutable
class KdNavDestination {
  const KdNavDestination({required this.icon, required this.label});

  final KdIconData icon;

  /// Short Spanish label, always visible («Inicio», «Citas»).
  final String label;
}

/// Bottom navigation of the patient app (the native mobile pattern,
/// DESIGN.md R-14): surface.raised with a hairline on top, labels always
/// visible, the selected destination marked with the accent and announced as
/// selected. The caller decides which destinations to show (permissions and
/// plan keep filtering the menu as today).
class KdBottomNavBar extends StatelessWidget {
  const KdBottomNavBar({
    super.key,
    required this.destinations,
    required this.selectedIndex,
    required this.onSelected,
  }) : assert(destinations.length >= 2 && destinations.length <= 5);

  final List<KdNavDestination> destinations;
  final int selectedIndex;
  final ValueChanged<int> onSelected;

  @override
  Widget build(BuildContext context) {
    final t = KdTokens.of(context);
    return DecoratedBox(
      decoration: BoxDecoration(border: Border(top: t.borderHairline)),
      child: NavigationBar(
        selectedIndex: selectedIndex,
        onDestinationSelected: onSelected,
        backgroundColor: t.surfaceRaised,
        surfaceTintColor: t.surfaceRaised,
        elevation: 0,
        shadowColor: t.surfaceRaised,
        indicatorColor: KdStyle.selectionTint(t, t.surfaceRaised),
        indicatorShape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(t.radiusFull),
        ),
        labelBehavior: NavigationDestinationLabelBehavior.alwaysShow,
        animationDuration: KdStyle.motion(context, t.motionDurationNormal),
        labelTextStyle: WidgetStateProperty.resolveWith(
          (states) => t.typeCaption.copyWith(
            fontWeight: t.typeWeightBold,
            color: states.contains(WidgetState.selected)
                ? t.colorAccent
                : t.textMuted,
          ),
        ),
        destinations: [
          for (final d in destinations)
            NavigationDestination(
              icon: KdIcon(d.icon, color: t.textMuted),
              selectedIcon: KdIcon(d.icon, color: t.colorAccent),
              label: d.label,
            ),
        ],
      ),
    );
  }
}
