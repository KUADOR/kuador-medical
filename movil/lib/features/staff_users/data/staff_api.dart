import '../../../core/api/api_client.dart';
import '../../../core/api/result_page.dart';
import 'staff_models.dart';

/// Staff operations of `contracts/user-registration.openapi.yaml`.
class StaffApi {
  StaffApi(this._api);

  final ApiClient _api;

  static const int pageSize = 20;

  static String _path(String id, [String suffix = '']) =>
      '/staff-users/${Uri.encodeComponent(id)}$suffix';

  Future<ResultPage<StaffUser>> list({
    String query = '',
    AccountStatus? status,
    int page = 0,
  }) async => ResultPage.fromJson(
    await _api.get(
      '/staff-users',
      query: {
        'q': query.trim(),
        'status': status?.code ?? '',
        'page': '$page',
        'size': '$pageSize',
      },
    ),
    StaffUser.fromJson,
  );

  Future<StaffUser> get(String id) async =>
      StaffUser.fromJson(await _api.get(_path(id)));

  /// Creates an `INVITED` account and sends the invitation. [id] is the client
  /// id, also sent as `Idempotency-Key`.
  Future<StaffUser> create({
    required String id,
    required StaffUserDraft draft,
  }) async => StaffUser.fromJson(
    await _api.post(
      '/staff-users',
      authenticated: true,
      headers: {'Idempotency-Key': id},
      body: {'id': id, ...draft.toJson()},
    ),
  );

  /// Partial update; [changes] holds only the changed fields.
  Future<StaffUser> update({
    required String id,
    required Map<String, Object?> changes,
    required int version,
  }) async => StaffUser.fromJson(
    await _api.patch(_path(id), body: {...changes, 'version': version}),
  );

  Future<void> resendInvitation(String id) async {
    await _api.post(_path(id, '/invitation'), authenticated: true);
  }

  Future<StaffUser> disable(String id) async => StaffUser.fromJson(
    await _api.post(_path(id, '/disable'), authenticated: true),
  );

  Future<StaffUser> enable(String id) async => StaffUser.fromJson(
    await _api.post(_path(id, '/enable'), authenticated: true),
  );

  Future<List<RoleAssignment>> replaceAssignments({
    required String id,
    required Map<String, Object?> request,
  }) async => RoleAssignment.listFromJson(
    await _api.put(_path(id, '/role-assignments'), body: request),
  );
}
