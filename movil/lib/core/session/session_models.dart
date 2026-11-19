import '../api/api_client.dart';
import '../api/api_exception.dart';

/// `ClinicSummary` and `BranchSummary` of the contract.
class NamedRef {
  const NamedRef({required this.id, required this.name});

  final String id;
  final String name;

  factory NamedRef.fromJson(Object? json) => NamedRef(
    id: requireField<String>(json, 'id'),
    name: requireField<String>(json, 'name'),
  );

  static List<NamedRef> listFromJson(Object? json) {
    if (json is! List) {
      throw const ApiException(code: ApiException.invalidResponseCode);
    }
    return [for (final item in json) NamedRef.fromJson(item)];
  }

  @override
  bool operator ==(Object other) =>
      other is NamedRef && other.id == id && other.name == name;

  @override
  int get hashCode => Object.hash(id, name);
}

/// Tokens of an `AuthenticatedResult`, with absolute expiry instants.
class SessionTokens {
  const SessionTokens({
    required this.accessToken,
    required this.accessExpiresAt,
    required this.refreshToken,
    required this.refreshExpiresAt,
  });

  final String accessToken;
  final DateTime accessExpiresAt;
  final String refreshToken;
  final DateTime refreshExpiresAt;

  /// True when the access token expires within [margin] of [now].
  bool accessExpiresSoon(DateTime now, {Duration margin = _margin}) =>
      !now.add(margin).isBefore(accessExpiresAt);

  bool refreshExpired(DateTime now) => !now.isBefore(refreshExpiresAt);

  static const Duration _margin = Duration(seconds: 30);

  /// Parses `AuthenticatedResult`, taking [now] as the issue instant.
  factory SessionTokens.fromAuthenticated(Object? json, DateTime now) {
    final type = requireField<String>(json, 'tokenType');
    if (type != 'Bearer') {
      throw const ApiException(code: ApiException.invalidResponseCode);
    }
    return SessionTokens(
      accessToken: requireField<String>(json, 'accessToken'),
      accessExpiresAt: now.add(
        Duration(seconds: requireField<int>(json, 'expiresIn')),
      ),
      refreshToken: requireField<String>(json, 'refreshToken'),
      refreshExpiresAt: now.add(
        Duration(seconds: requireField<int>(json, 'refreshExpiresIn')),
      ),
    );
  }

  Map<String, Object> toStorage() => {
    'accessToken': accessToken,
    'accessExpiresAt': accessExpiresAt.toUtc().toIso8601String(),
    'refreshToken': refreshToken,
    'refreshExpiresAt': refreshExpiresAt.toUtc().toIso8601String(),
  };

  /// Returns null when the stored value is missing or corrupt.
  static SessionTokens? fromStorage(Map<String, dynamic> json) {
    try {
      return SessionTokens(
        accessToken: json['accessToken'] as String,
        accessExpiresAt: DateTime.parse(json['accessExpiresAt'] as String),
        refreshToken: json['refreshToken'] as String,
        refreshExpiresAt: DateTime.parse(json['refreshExpiresAt'] as String),
      );
    } on Object {
      return null;
    }
  }
}

/// `AccountKind` of the contract.
enum AccountKind {
  staff,
  patient,
  guardian;

  static AccountKind parse(Object? value) => switch (value) {
    'STAFF' => AccountKind.staff,
    'PATIENT' => AccountKind.patient,
    'GUARDIAN' => AccountKind.guardian,
    _ => throw const ApiException(code: ApiException.invalidResponseCode),
  };
}

/// Response of `GET /me` (`CurrentSession` in the contract).
class CurrentSession {
  const CurrentSession({
    required this.accountId,
    required this.kind,
    required this.isPlatform,
    required this.displayName,
    required this.clinic,
    required this.activeBranch,
    required this.availableBranches,
    required this.roles,
    required this.permissions,
    required this.enabledModules,
    required this.mustChangePassword,
    this.patientId,
  });

  final String accountId;
  final AccountKind kind;

  /// `scope: PLATFORM` (SUPER_ADMIN account).
  final bool isPlatform;
  final String displayName;
  final NamedRef? clinic;
  final NamedRef? activeBranch;
  final List<NamedRef> availableBranches;
  final List<String> roles;
  final Set<String> permissions;
  final Set<String> enabledModules;
  final bool mustChangePassword;
  final String? patientId;

  bool can(String permission) => permissions.contains(permission);

  bool hasModule(String module) => enabledModules.contains(module);

  /// The active assignment has the OWNER role (only an OWNER assigns OWNER or
  /// edits ADMIN); the API enforces it, the UI only offers what can succeed.
  bool get isOwner => roles.contains('OWNER');

  /// Staff with more than one assigned branch can switch.
  bool get canSwitchBranch =>
      kind == AccountKind.staff && availableBranches.length > 1;

  factory CurrentSession.fromJson(Object? json) {
    final map = json is Map<String, dynamic>
        ? json
        : throw const ApiException(code: ApiException.invalidResponseCode);
    List<String> strings(String name) {
      final list = requireField<List<dynamic>>(map, name);
      if (list.any((e) => e is! String)) {
        throw const ApiException(code: ApiException.invalidResponseCode);
      }
      return list.cast<String>();
    }

    final clinic = map['clinic'];
    final branch = map['activeBranch'];
    final scope = requireField<String>(map, 'scope');
    return CurrentSession(
      accountId: requireField<String>(map, 'accountId'),
      kind: AccountKind.parse(map['kind']),
      isPlatform: scope == 'PLATFORM',
      displayName: requireField<String>(map, 'displayName'),
      clinic: clinic == null ? null : NamedRef.fromJson(clinic),
      activeBranch: branch == null ? null : NamedRef.fromJson(branch),
      availableBranches: NamedRef.listFromJson(map['availableBranches']),
      roles: strings('roles'),
      permissions: strings('permissions').toSet(),
      enabledModules: strings('enabledModules').toSet(),
      mustChangePassword: requireField<bool>(map, 'mustChangePassword'),
      patientId: optionalField<String>(map, 'patientId'),
    );
  }
}
