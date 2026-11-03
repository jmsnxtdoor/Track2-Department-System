import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:http/http.dart' as http;

import 'student_models.dart';

const defaultApiBaseUrl = String.fromEnvironment(
  'API_BASE_URL',
  defaultValue: 'http://10.0.2.2:8080/api',
);

class ApiException implements Exception {
  const ApiException(this.message);

  final String message;

  @override
  String toString() => message;
}

class StudentApiClient {
  StudentApiClient._({
    required String apiBaseUrl,
    required String token,
    required http.Client client,
  })  : _apiBaseUrl = _normalizeBaseUrl(apiBaseUrl),
        _token = token,
        _client = client;

  final String _apiBaseUrl;
  final http.Client _client;
  String _token;

  static Future<StudentApiClient> signIn({
    required String apiBaseUrl,
    required String username,
    required String password,
    http.Client? client,
  }) async {
    final httpClient = client ?? http.Client();
    final token = base64Encode(utf8.encode('$username:$password'));
    try {
      final api = StudentApiClient._(
        apiBaseUrl: apiBaseUrl,
        token: token,
        client: httpClient,
      );
      final response = await api._get('/me');
      final roles = response['roles'];
      if (roles is! List<dynamic>) {
        throw const ApiException(
          'The server returned an unexpected sign-in response.',
        );
      }
      if (!roles.contains('ROLE_STUDENT')) {
        throw const ApiException('This app is for student accounts only.');
      }
      return api;
    } on Exception {
      httpClient.close();
      rethrow;
    }
  }

  Future<StudentSummary> loadSummary() async {
    try {
      return StudentSummary.fromJson(await _get('/students/me/summary'));
    } on TypeError {
      throw const ApiException(
        'The server returned student data in an unexpected format.',
      );
    } on FormatException {
      throw const ApiException(
        'The server returned student data in an unexpected format.',
      );
    }
  }

  Future<List<StudentLedgerEntry>> loadLedger() async {
    try {
      final rows = await _getList('/students/me/ledger');
      return rows.map(StudentLedgerEntry.fromJson).toList();
    } on TypeError {
      throw const ApiException(
        'The server returned payment data in an unexpected format.',
      );
    } on FormatException {
      throw const ApiException(
        'The server returned payment data in an unexpected format.',
      );
    }
  }

  Future<List<StudentAttendanceEntry>> loadAttendance() async {
    try {
      final rows = await _getList('/students/me/attendance');
      return rows.map(StudentAttendanceEntry.fromJson).toList();
    } on TypeError {
      throw const ApiException(
        'The server returned attendance data in an unexpected format.',
      );
    } on FormatException {
      throw const ApiException(
        'The server returned attendance data in an unexpected format.',
      );
    }
  }

  Future<Map<String, dynamic>> _get(String path) async {
    final response = await _send(path);
    dynamic decoded;
    try {
      decoded = jsonDecode(response.body);
    } on FormatException {
      throw const ApiException('The server returned an unreadable response.');
    }
    if (decoded is! Map<String, dynamic>) {
      throw const ApiException('The server returned an unexpected response.');
    }
    return decoded;
  }

  Future<List<Map<String, dynamic>>> _getList(String path) async {
    final response = await _send(path);
    dynamic decoded;
    try {
      decoded = jsonDecode(response.body);
    } on FormatException {
      throw const ApiException('The server returned an unreadable response.');
    }
    if (decoded is! List<dynamic>) {
      throw const ApiException('The server returned an unexpected response.');
    }
    if (decoded.any((row) => row is! Map<String, dynamic>)) {
      throw const ApiException(
        'The server returned a list in an unexpected format.',
      );
    }
    return decoded.cast<Map<String, dynamic>>();
  }

  Future<http.Response> _send(String path) async {
    if (_token.isEmpty) {
      throw const ApiException('You have been signed out. Sign in again.');
    }
    try {
      final response = await _client.get(
        Uri.parse('$_apiBaseUrl$path'),
        headers: {'Authorization': 'Basic $_token'},
      ).timeout(const Duration(seconds: 15));
      if (response.statusCode >= 200 && response.statusCode < 300) {
        return response;
      }
      throw ApiException(_messageForStatus(response.statusCode));
    } on ApiException {
      rethrow;
    } on TimeoutException {
      throw const ApiException(
        'The server took too long to respond. Check your connection and try again.',
      );
    } on SocketException {
      throw const ApiException(
        'Could not reach the server. Check the API URL and your network connection.',
      );
    } on http.ClientException {
      throw const ApiException(
        'Could not connect to the server. Check the API URL and try again.',
      );
    } on FormatException {
      throw const ApiException('The server returned an unreadable response.');
    }
  }

  static String _messageForStatus(int statusCode) {
    return switch (statusCode) {
      401 => 'Username or password is incorrect. Try again.',
      403 => 'This account does not have permission to use the student app.',
      404 => 'The requested student information was not found.',
      409 =>
        'Your student account is not linked to a student record. Contact the department administrator.',
      >= 500 => 'The department server is unavailable. Try again later.',
      _ => 'The request could not be completed (error $statusCode). Try again.',
    };
  }

  static String _normalizeBaseUrl(String value) {
    final trimmed = value.trim().replaceFirst(RegExp(r'/+$'), '');
    final uri = Uri.tryParse(trimmed);
    if (uri == null ||
        !uri.hasScheme ||
        !uri.hasAuthority ||
        !{'http', 'https'}.contains(uri.scheme.toLowerCase()) ||
        uri.userInfo.isNotEmpty ||
        uri.hasQuery ||
        uri.hasFragment) {
      throw const ApiException(
        'Enter a complete server URL, such as http://10.0.2.2:8080/api.',
      );
    }
    final path = uri.path.replaceFirst(RegExp(r'/+$'), '');
    final apiPath = path.endsWith('/api') ? path : '$path/api';
    return uri
        .replace(path: apiPath)
        .toString()
        .replaceFirst(RegExp(r'/+$'), '');
  }

  void logout() {
    _token = '';
    _client.close();
  }
}
