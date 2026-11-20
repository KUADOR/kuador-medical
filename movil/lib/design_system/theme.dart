import 'package:flutter/material.dart';

import 'tokens.g.dart';

/// App theme built only from the design tokens (ADR-006).
///
/// Use [light] and [dark] with [mode] in the MaterialApp, plus [builder] so
/// that the platform "reduce motion" setting removes page transitions:
///
/// ```dart
/// MaterialApp(
///   theme: KdTheme.light,
///   darkTheme: KdTheme.dark,
///   themeMode: KdTheme.mode,
///   builder: KdTheme.builder,
/// )
/// ```
///
/// Screens read colors, spacing and radii from `KdTokens.of(context)` or
/// `KdConstants`, never as literals (ADR-007, see the style guard test).
abstract final class KdTheme {
  /// Light theme.
  static final ThemeData light = KdGeneratedTheme.light;

  /// Dark theme.
  static final ThemeData dark = KdGeneratedTheme.dark;

  /// Light or dark follows the operating system; no stored preference.
  static const ThemeMode mode = ThemeMode.system;

  /// Whether the platform asks to reduce or remove animations.
  static bool reduceMotion(BuildContext context) =>
      MediaQuery.maybeDisableAnimationsOf(context) ?? false;

  /// [duration] (a motion token), or zero when animations are disabled.
  static Duration duration(BuildContext context, Duration duration) =>
      reduceMotion(context) ? Duration.zero : duration;

  /// MaterialApp.builder: removes page transitions when animations are
  /// disabled, keeping the rest of the active theme.
  static Widget builder(BuildContext context, Widget? child) {
    final content = child ?? const SizedBox.shrink();
    if (!reduceMotion(context)) return content;
    final theme = Theme.of(context);
    return Theme(
      data: theme.copyWith(pageTransitionsTheme: _noTransitions),
      child: content,
    );
  }

  static const _noTransitions = PageTransitionsTheme(
    builders: <TargetPlatform, PageTransitionsBuilder>{
      TargetPlatform.android: _NoTransitionsBuilder(),
      TargetPlatform.fuchsia: _NoTransitionsBuilder(),
      TargetPlatform.iOS: _NoTransitionsBuilder(),
      TargetPlatform.linux: _NoTransitionsBuilder(),
      TargetPlatform.macOS: _NoTransitionsBuilder(),
      TargetPlatform.windows: _NoTransitionsBuilder(),
    },
  );
}

/// Shows the new page at once, with no animation.
class _NoTransitionsBuilder extends PageTransitionsBuilder {
  const _NoTransitionsBuilder();

  @override
  Widget buildTransitions<T>(
    PageRoute<T> route,
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
    Widget child,
  ) => child;
}
