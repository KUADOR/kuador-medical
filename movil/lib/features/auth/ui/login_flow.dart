import 'package:flutter/material.dart';

import '../../../app_scope.dart';
import '../data/auth_models.dart';
import 'branch_selection_screen.dart';
import 'clinic_selection_screen.dart';

/// Continues a login step: signs in, or asks for the clinic or the branch.
///
/// When the session becomes active the app shows the home screen and closes
/// the pushed auth screens.
Future<void> continueLogin(BuildContext context, LoginResult result) async {
  switch (result) {
    case Authenticated(:final tokens):
      await AppScope.of(context).session.signIn(tokens);
    case ClinicSelectionRequired():
      await Navigator.of(context).push(
        MaterialPageRoute<void>(
          builder: (_) => ClinicSelectionScreen(step: result),
        ),
      );
    case BranchSelectionRequired():
      await Navigator.of(context).push(
        MaterialPageRoute<void>(
          builder: (_) => BranchSelectionScreen(step: result),
        ),
      );
  }
}
