import 'api_client.dart';

/// A page of a paged list (`StaffUserPage`, `PatientPage`).
class ResultPage<T> {
  const ResultPage({
    required this.items,
    required this.page,
    required this.total,
  });

  final List<T> items;
  final int page;
  final int total;

  static ResultPage<T> fromJson<T>(Object? json, T Function(Object?) item) {
    final items = requireField<List<dynamic>>(json, 'items');
    return ResultPage(
      items: [for (final i in items) item(i)],
      page: requireField<int>(json, 'page'),
      total: requireField<int>(json, 'totalItems'),
    );
  }
}
