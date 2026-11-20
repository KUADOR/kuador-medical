import 'tokens.g.dart';

/// Layout values derived from the tokens, named so screens never compute
/// spacing or sizes by hand (ADR-007).
abstract final class KdLayout {
  /// Space a scrollable list leaves free at its end so the floating action
  /// button never covers the last item.
  static const double fabClearance = KdConstants.space12 * 2;

  /// Readable width cap of a single-column form or message (sign-in, status,
  /// errors) on tablets and in landscape.
  static const double formMaxWidth = KdConstants.space12 * 10;

  /// Readable width cap of a module page (lists, details, home) on tablets.
  static const double pageMaxWidth = KdConstants.space12 * 12.5;

  /// Size of the icon that heads a confirmation (a sent link, a verified
  /// email) or a full-screen message (an error).
  static const double statusIconSize = KdConstants.space12;
}
