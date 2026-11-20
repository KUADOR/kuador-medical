import 'tokens.g.dart';

/// Layout values derived from the tokens, named so screens never compute
/// spacing by hand (ADR-007).
abstract final class KdLayout {
  /// Space a scrollable list leaves free at its end so the floating action
  /// button never covers the last item.
  static const double fabClearance = KdConstants.space12 * 2;
}
