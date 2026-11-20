import 'tokens.g.dart';

/// Layout values derived from the tokens, named so screens never compute
/// spacing by hand (ADR-007).
abstract final class KdLayout {
  /// Space a scrollable list leaves free at its end so the floating action
  /// button never covers the last item.
  static const double fabClearance = KdConstants.space12 * 2;

  /// Readable width of a form page on tablets and in landscape.
  static const double formMaxWidth = 480;

  /// Size of the icon that heads a confirmation (a sent link, a verified
  /// email).
  static const double statusIconSize = KdConstants.space12;
}
