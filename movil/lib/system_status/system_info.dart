/// Health status reported by the API (`HealthStatus` in the contract).
enum HealthStatus {
  up,
  down;

  static HealthStatus parse(Object? value) => switch (value) {
    'UP' => HealthStatus.up,
    'DOWN' => HealthStatus.down,
    _ => throw FormatException('Unknown health status: $value'),
  };
}

/// Response of `GET /system/info` (`SystemInfo` in the contract).
class SystemInfo {
  const SystemInfo({
    required this.name,
    required this.version,
    required this.status,
    required this.database,
  });

  final String name;
  final String version;
  final HealthStatus status;
  final HealthStatus database;

  factory SystemInfo.fromJson(Map<String, dynamic> json) {
    final name = json['name'];
    final version = json['version'];
    if (name is! String || version is! String) {
      throw const FormatException('Missing name or version');
    }
    return SystemInfo(
      name: name,
      version: version,
      status: HealthStatus.parse(json['status']),
      database: HealthStatus.parse(json['database']),
    );
  }
}
