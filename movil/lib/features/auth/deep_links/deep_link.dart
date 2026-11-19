import 'dart:async';

import 'package:app_links/app_links.dart';

/// Link that opens one of the one-time token flows (ADR-014):
/// `https://<host>/<route>#t=<token>`.
sealed class DeepLink {
  const DeepLink(this.token);

  /// One-time token taken from the URL fragment. Never logged.
  final String token;
}

class InvitationLink extends DeepLink {
  const InvitationLink(super.token);
}

class PasswordResetLink extends DeepLink {
  const PasswordResetLink(super.token);
}

class EmailVerificationLink extends DeepLink {
  const EmailVerificationLink(super.token);
}

/// Routes fixed in the api (`application.yaml`) and in the web
/// (`docker-entrypoint.d/50-app-links.sh`); see docs/deployment.md.
const String invitationPath = '/invitation';
const String passwordResetPath = '/password-reset';
const String emailVerificationPath = '/email-verification';

/// Parses an incoming URL into a [DeepLink], or null when it is not one of
/// ours. Only `https` links of [allowedHost] are accepted (any host when it is
/// empty, for local builds without a deep link domain).
DeepLink? parseDeepLink(Uri uri, {required String allowedHost}) {
  if (uri.scheme != 'https') return null;
  if (allowedHost.isNotEmpty && uri.host.toLowerCase() != allowedHost) {
    return null;
  }
  final String? token;
  try {
    token = Uri.splitQueryString(uri.fragment)['t'];
  } on FormatException {
    return null;
  }
  // `OneTimeToken` in the contract: 16 to 512 characters.
  if (token == null || token.length < 16 || token.length > 512) return null;

  var path = uri.path;
  if (path.length > 1 && path.endsWith('/')) {
    path = path.substring(0, path.length - 1);
  }
  return switch (path) {
    invitationPath => InvitationLink(token),
    passwordResetPath => PasswordResetLink(token),
    emailVerificationPath => EmailVerificationLink(token),
    _ => null,
  };
}

/// Source of the URLs that open the app (App Links / Universal Links).
abstract interface class DeepLinkSource {
  /// Emits the link that launched the app, then each link opened later.
  Stream<Uri> get links;
}

/// Platform implementation with `app_links`.
class PlatformDeepLinkSource implements DeepLinkSource {
  PlatformDeepLinkSource([AppLinks? appLinks])
    : _appLinks = appLinks ?? AppLinks();

  final AppLinks _appLinks;

  @override
  Stream<Uri> get links => _appLinks.uriLinkStream;
}

/// Manually driven source for tests.
class FakeDeepLinkSource implements DeepLinkSource {
  final StreamController<Uri> _controller = StreamController<Uri>.broadcast();

  void open(Uri uri) => _controller.add(uri);

  @override
  Stream<Uri> get links => _controller.stream;
}
