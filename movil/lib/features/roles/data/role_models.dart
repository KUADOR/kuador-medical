import '../../../core/api/api_client.dart';
import '../../../core/api/api_exception.dart';

/// `Role` of the contract.
class Role {
  const Role({
    required this.id,
    required this.name,
    required this.systemRole,
    required this.clinical,
    required this.permissions,
    required this.version,
    this.description,
    this.systemKey,
  });

  final String id;
  final String name;
  final String? description;

  /// `OWNER` or `ADMIN` for the protected roles; null otherwise.
  final String? systemKey;
  final bool systemRole;

  /// Users with a clinical role need a professional registration.
  final bool clinical;
  final Set<String> permissions;
  final int version;

  bool get isOwner => systemKey == 'OWNER';
  bool get isAdmin => systemKey == 'ADMIN';

  factory Role.fromJson(Object? json) {
    final map = json is Map<String, dynamic>
        ? json
        : throw const ApiException(code: ApiException.invalidResponseCode);
    final permissions = requireField<List<dynamic>>(map, 'permissions');
    if (permissions.any((p) => p is! String)) {
      throw const ApiException(code: ApiException.invalidResponseCode);
    }
    return Role(
      id: requireField<String>(map, 'id'),
      name: requireField<String>(map, 'name'),
      description: optionalField<String>(map, 'description'),
      systemKey: optionalField<String>(map, 'systemKey'),
      systemRole: requireField<bool>(map, 'systemRole'),
      clinical: requireField<bool>(map, 'clinical'),
      permissions: permissions.cast<String>().toSet(),
      version: requireField<int>(map, 'version'),
    );
  }

  static List<Role> listFromJson(Object? json) {
    if (json is! List) {
      throw const ApiException(code: ApiException.invalidResponseCode);
    }
    return [for (final item in json) Role.fromJson(item)];
  }
}

/// `PermissionInfo` of the contract.
class PermissionInfo {
  const PermissionInfo({
    required this.code,
    required this.module,
    required this.description,
  });

  final String code;
  final String module;

  /// Spanish description shown to people.
  final String description;

  factory PermissionInfo.fromJson(Object? json) => PermissionInfo(
    code: requireField<String>(json, 'code'),
    module: requireField<String>(json, 'module'),
    description: requireField<String>(json, 'description'),
  );

  /// Clinic-scoped permissions only: `PLATFORM` ones are never assignable.
  static List<PermissionInfo> listFromJson(Object? json) {
    if (json is! List) {
      throw const ApiException(code: ApiException.invalidResponseCode);
    }
    return [
      for (final item in json)
        if (requireField<String>(item, 'scope') == 'TENANT')
          PermissionInfo.fromJson(item),
    ];
  }
}

/// Body of `POST /roles` and `PUT /roles/{id}` (without `id` and `version`).
class RoleDraft {
  const RoleDraft({
    required this.name,
    required this.description,
    required this.clinical,
    required this.permissions,
  });

  final String name;
  final String? description;
  final bool clinical;
  final Set<String> permissions;

  Map<String, Object?> toJson() => {
    'name': name,
    'description': (description == null || description!.isEmpty)
        ? null
        : description,
    'clinical': clinical,
    'permissions': permissions.toList()..sort(),
  };
}

/// Spanish names of the permission modules.
String moduleLabel(String module) => switch (module) {
  'identity' => 'Personal, roles y accesos',
  'patients' => 'Pacientes',
  'other' => 'Otros permisos',
  _ => module,
};
