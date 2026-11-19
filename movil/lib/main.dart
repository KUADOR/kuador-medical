import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;

import 'config.dart';
import 'system_status/system_info_client.dart';
import 'system_status/system_status_screen.dart';

void main() {
  final config = AppConfig.fromEnvironment();
  final client = SystemInfoClient(
    httpClient: http.Client(),
    baseUrl: config.apiBaseUrl,
  );
  runApp(MedicalKuadorApp(systemInfoClient: client));
}

class MedicalKuadorApp extends StatelessWidget {
  const MedicalKuadorApp({super.key, required this.systemInfoClient});

  final SystemInfoClient systemInfoClient;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Medical Kuador',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF00695C)),
      ),
      darkTheme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF00695C),
          brightness: Brightness.dark,
        ),
      ),
      home: SystemStatusScreen(client: systemInfoClient),
    );
  }
}
