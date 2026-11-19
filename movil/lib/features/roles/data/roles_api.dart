import '../../../core/api/api_client.dart';
import 'role_models.dart';

/// Roles operations of `contracts/user-registration.openapi.yaml`.
class RolesApi {
  RolesApi(this._api);

  final ApiClient _api;

  Future<List<PermissionInfo>> listPermissions() async =>
      PermissionInfo.listFromJson(await _api.get('/permissions'));

  Future<List<Role>> listRoles() async =>
      Role.listFromJson(await _api.get('/roles'));

  Future<Role> getRole(String id) async =>
      Role.fromJson(await _api.get('/roles/${Uri.encodeComponent(id)}'));

  /// [id] is the client id, also sent as `Idempotency-Key`.
  Future<Role> createRole({
    required String id,
    required RoleDraft draft,
  }) async => Role.fromJson(
    await _api.post(
      '/roles',
      authenticated: true,
      headers: {'Idempotency-Key': id},
      body: {'id': id, ...draft.toJson()},
    ),
  );

  Future<Role> updateRole({
    required String id,
    required RoleDraft draft,
    required int version,
  }) async => Role.fromJson(
    await _api.put(
      '/roles/${Uri.encodeComponent(id)}',
      body: {...draft.toJson(), 'version': version},
    ),
  );

  Future<void> deleteRole(String id) async {
    await _api.delete('/roles/${Uri.encodeComponent(id)}');
  }
}
