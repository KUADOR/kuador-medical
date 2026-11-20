import 'package:flutter_test/flutter_test.dart';

import '../../support/design_system_harness.dart';
import 'gallery.dart';

/// Goldens (light and dark) and accessibility guidelines (tap targets,
/// labeled targets, text contrast) of every widget of the library.
void main() {
  group('accessibility', () {
    testA11yGuidelines('controls', () => const ControlsGallery());
    testA11yGuidelines('list and cards', () => const ListGallery());
    testA11yGuidelines('empty state', () => const EmptyGallery());
    testA11yGuidelines('error state', () => const ErrorGallery());
    testA11yGuidelines(
      'loading state',
      () => const LoadingGallery(),
      reduceMotion: true,
    );
    testA11yGuidelines('bottom sheet', () => const OverlayGallery.sheet());
    testA11yGuidelines('dialog', () => const OverlayGallery.dialog());
  });

  group('goldens', () {
    testGoldens('widgets_controls', () => const ControlsGallery());
    testGoldens('widgets_list', () => const ListGallery());
    testGoldens('widgets_empty', () => const EmptyGallery());
    testGoldens('widgets_error', () => const ErrorGallery());
    testGoldens(
      'widgets_loading',
      () => const LoadingGallery(),
      reduceMotion: true,
    );
    testGoldens('widgets_sheet', () => const OverlayGallery.sheet());
    testGoldens('widgets_dialog', () => const OverlayGallery.dialog());
  });
}
