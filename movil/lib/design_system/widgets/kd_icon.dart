import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';

import 'kd_style.dart';

/// One icon of the design system set (Lucide line icons, `assets/icons/`,
/// copied from `design/icons/`).
@immutable
class KdIconData {
  const KdIconData(this.name);

  /// File name without extension, the same as in `design/icons/`.
  final String name;

  /// Asset path of the SVG file.
  String get assetPath => 'assets/icons/$name.svg';

  @override
  bool operator ==(Object other) => other is KdIconData && other.name == name;

  @override
  int get hashCode => name.hashCode;

  @override
  String toString() => 'KdIconData($name)';
}

/// The icon set. Add an icon to `design/icons/` first (DESIGN.md,
/// Iconografía), copy it to `assets/icons/` and list it here.
abstract final class KdIcons {
  static const arrowLeft = KdIconData('arrow-left');
  static const bell = KdIconData('bell');
  static const building = KdIconData('building-2');
  static const calendarCheck = KdIconData('calendar-check');
  static const calendarClock = KdIconData('calendar-clock');
  static const calendarPlus = KdIconData('calendar-plus');
  static const calendarX = KdIconData('calendar-x');
  static const calendar = KdIconData('calendar');
  static const check = KdIconData('check');
  static const chevronDown = KdIconData('chevron-down');
  static const chevronLeft = KdIconData('chevron-left');
  static const chevronRight = KdIconData('chevron-right');
  static const chevronUp = KdIconData('chevron-up');
  static const circleAlert = KdIconData('circle-alert');
  static const circleCheck = KdIconData('circle-check');
  static const clipboardList = KdIconData('clipboard-list');
  static const clock = KdIconData('clock');
  static const ellipsisVertical = KdIconData('ellipsis-vertical');
  static const externalLink = KdIconData('external-link');
  static const eyeOff = KdIconData('eye-off');
  static const eye = KdIconData('eye');
  static const fileText = KdIconData('file-text');
  static const funnel = KdIconData('funnel');
  static const heartPulse = KdIconData('heart-pulse');
  static const house = KdIconData('house');
  static const info = KdIconData('info');
  static const lock = KdIconData('lock');
  static const logIn = KdIconData('log-in');
  static const logOut = KdIconData('log-out');
  static const mail = KdIconData('mail');
  static const mapPin = KdIconData('map-pin');
  static const menu = KdIconData('menu');
  static const minus = KdIconData('minus');
  static const pencil = KdIconData('pencil');
  static const phone = KdIconData('phone');
  static const pill = KdIconData('pill');
  static const plus = KdIconData('plus');
  static const refresh = KdIconData('refresh-cw');
  static const search = KdIconData('search');
  static const settings = KdIconData('settings');
  static const stethoscope = KdIconData('stethoscope');
  static const trash = KdIconData('trash-2');
  static const triangleAlert = KdIconData('triangle-alert');
  static const userPlus = KdIconData('user-plus');
  static const user = KdIconData('user');
  static const users = KdIconData('users');
  static const x = KdIconData('x');

  /// Every icon of the set.
  static const List<KdIconData> all = [
    arrowLeft, bell, building, calendarCheck, calendarClock, calendarPlus, //
    calendarX, calendar, check, chevronDown, chevronLeft, chevronRight,
    chevronUp, circleAlert, circleCheck, clipboardList, clock,
    ellipsisVertical, externalLink, eyeOff, eye, fileText, funnel,
    heartPulse, house, info, lock, logIn, logOut, mail, mapPin, menu, minus,
    pencil, phone, pill, plus, refresh, search, settings, stethoscope, trash,
    triangleAlert, userPlus, user, users, x,
  ];
}

/// Draws a [KdIconData] in the color of the surrounding [IconTheme] (the
/// SVG strokes use `currentColor`).
///
/// Decorative by default (hidden from screen readers); pass [semanticLabel]
/// when the icon carries meaning that no nearby text says (DESIGN.md: an
/// icon without visible text always has an accessible name).
class KdIcon extends StatelessWidget {
  const KdIcon(
    this.icon, {
    super.key,
    this.size,
    this.color,
    this.semanticLabel,
  });

  final KdIconData icon;

  /// Visual size; defaults to the [IconTheme] size (24).
  final double? size;

  /// Defaults to the [IconTheme] color.
  final Color? color;

  final String? semanticLabel;

  @override
  Widget build(BuildContext context) {
    final iconTheme = IconTheme.of(context);
    final side = size ?? iconTheme.size ?? KdStyle.iconSize;
    final paint =
        color ?? iconTheme.color ?? DefaultTextStyle.of(context).style.color!;
    // Like Icon: a box of the icon size with the drawing centered, so tight
    // constraints (a field prefix, a 48 tap area) never stretch it.
    final picture = SizedBox.square(
      dimension: side,
      child: Center(
        child: SvgPicture.asset(
          icon.assetPath,
          width: side,
          height: side,
          theme: SvgTheme(currentColor: paint),
          excludeFromSemantics: true,
        ),
      ),
    );
    final label = semanticLabel;
    if (label == null) return ExcludeSemantics(child: picture);
    return Semantics(label: label, image: true, child: picture);
  }
}
