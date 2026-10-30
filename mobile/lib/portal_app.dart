import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;

import 'api_client.dart';
import 'login_page.dart';
import 'student_home.dart';

class StudentPortalApp extends StatefulWidget {
  const StudentPortalApp({
    super.key,
    this.apiBaseUrl = defaultApiBaseUrl,
    this.clientFactory,
  });

  final String apiBaseUrl;
  final http.Client Function()? clientFactory;

  @override
  State<StudentPortalApp> createState() => _StudentPortalAppState();
}

class _StudentPortalAppState extends State<StudentPortalApp> {
  StudentApiClient? _apiClient;

  @override
  Widget build(BuildContext context) {
    const navy = Color(0xFF1E3A8A);
    final scheme = ColorScheme.fromSeed(
      seedColor: navy,
      brightness: Brightness.light,
    ).copyWith(
      primary: navy,
      surface: Colors.white,
    );
    return MaterialApp(
      title: 'Track 2 Student Portal',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: scheme,
        scaffoldBackgroundColor: const Color(0xFFF8FAFC),
        appBarTheme: const AppBarTheme(
          backgroundColor: Color(0xFFF8FAFC),
          surfaceTintColor: Colors.transparent,
          foregroundColor: Color(0xFF0F172A),
        ),
        cardTheme: CardThemeData(
          color: Colors.white,
          elevation: 0,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
            side: const BorderSide(color: Color(0xFFE2E8F0)),
          ),
        ),
      ),
      home: _apiClient == null
          ? LoginPage(
              apiBaseUrl: widget.apiBaseUrl,
              clientFactory: widget.clientFactory,
              onSignedIn: (client) => setState(() => _apiClient = client),
            )
          : StudentHome(
              apiClient: _apiClient!,
              onLogout: () => setState(() => _apiClient = null),
            ),
    );
  }
}
