import 'dart:convert';

import 'package:department_student_portal/api_client.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  test('uses the linked student API and parses real response fields', () async {
    final paths = <String>[];
    final expectedAuthorization =
        'Basic ${base64Encode(utf8.encode('student-one:password'))}';
    final mock = MockClient((request) async {
      paths.add(request.url.path);
      expect(request.headers['authorization'], expectedAuthorization);
      return switch (request.url.path) {
        '/api/me' => http.Response(
            jsonEncode({
              'username': 'student-one',
              'roles': ['ROLE_STUDENT']
            }),
            200,
          ),
        '/api/students/me/summary' => http.Response(
            jsonEncode({
              'username': 'student-one',
              'student': {
                'studentNumber': 'S-001',
                'fullName': 'Sample Student',
                'course': 'BSCS',
                'yearLevel': '2',
                'section': 'A',
              },
              'unpaidBalance': 125.5,
              'attendance': {
                'total': 2,
                'present': 1,
                'late': 1,
                'absent': 0,
                'excused': 0,
              },
            }),
            200,
          ),
        '/api/students/me/ledger' => http.Response('[]', 200),
        '/api/students/me/attendance' => http.Response('[]', 200),
        _ => http.Response('Not found', 404),
      };
    });

    final api = await StudentApiClient.signIn(
      apiBaseUrl: 'http://localhost:8080',
      username: 'student-one',
      password: 'password',
      client: mock,
    );
    final summary = await api.loadSummary();
    await api.loadLedger();
    await api.loadAttendance();
    api.logout();

    expect(summary.student.studentNumber, 'S-001');
    expect(summary.unpaidBalance, 125.5);
    expect(summary.attendance.present, 1);
    expect(paths, [
      '/api/me',
      '/api/students/me/summary',
      '/api/students/me/ledger',
      '/api/students/me/attendance',
    ]);
  });

  test('rejects non-student accounts', () async {
    final mock = MockClient((_) async => http.Response(
          jsonEncode({
            'username': 'officer',
            'roles': ['ROLE_OFFICER']
          }),
          200,
        ));

    await expectLater(
      StudentApiClient.signIn(
        apiBaseUrl: 'http://localhost:8080/api',
        username: 'officer',
        password: 'password',
        client: mock,
      ),
      throwsA(
        isA<ApiException>().having(
          (failure) => failure.message,
          'message',
          'This app is for student accounts only.',
        ),
      ),
    );
  });

  test('explains unauthorized and unlinked account responses', () async {
    final unauthorizedClient = MockClient((_) async => http.Response('', 401));
    await expectLater(
      StudentApiClient.signIn(
        apiBaseUrl: 'http://localhost:8080/api',
        username: 'student',
        password: 'password',
        client: unauthorizedClient,
      ),
      throwsA(
        isA<ApiException>().having(
          (failure) => failure.message,
          'message',
          'Username or password is incorrect. Try again.',
        ),
      ),
    );

    final unlinkedClient = MockClient((request) async {
      if (request.url.path == '/api/me') {
        return http.Response(
          jsonEncode({
            'username': 'student',
            'roles': ['ROLE_STUDENT'],
          }),
          200,
        );
      }
      return http.Response('', 409);
    });
    final api = await StudentApiClient.signIn(
      apiBaseUrl: 'http://localhost:8080/api',
      username: 'student',
      password: 'password',
      client: unlinkedClient,
    );
    await expectLater(
      api.loadSummary(),
      throwsA(
        isA<ApiException>().having(
          (failure) => failure.message,
          'message',
          'Your student account is not linked to a student record. Contact the department administrator.',
        ),
      ),
    );
    api.logout();
  });

  test('rejects malformed balance data instead of reporting a false zero',
      () async {
    final mock = MockClient((request) async => switch (request.url.path) {
          '/api/me' => http.Response(
              jsonEncode({
                'username': 'student',
                'roles': ['ROLE_STUDENT'],
              }),
              200,
            ),
          '/api/students/me/summary' => http.Response(
              jsonEncode({
                'username': 'student',
                'student': const {},
                'unpaidBalance': 'not-a-number',
                'attendance': {
                  'total': 0,
                  'present': 0,
                  'late': 0,
                  'absent': 0,
                  'excused': 0,
                },
              }),
              200,
            ),
          _ => http.Response('[]', 200),
        });

    final api = await StudentApiClient.signIn(
      apiBaseUrl: 'http://localhost:8080/api',
      username: 'student',
      password: 'password',
      client: mock,
    );

    await expectLater(
      api.loadSummary(),
      throwsA(
        isA<ApiException>().having(
          (failure) => failure.message,
          'message',
          'The server returned student data in an unexpected format.',
        ),
      ),
    );
    api.logout();
  });

  test('rejects malformed list rows instead of silently dropping them',
      () async {
    final mock = MockClient((request) async => switch (request.url.path) {
          '/api/me' => http.Response(
              jsonEncode({
                'username': 'student',
                'roles': ['ROLE_STUDENT'],
              }),
              200,
            ),
          '/api/students/me/ledger' => http.Response(
              jsonEncode([
                {'kind': 'FINE', 'amount': 100},
                'invalid row',
              ]),
              200,
            ),
          _ => http.Response('[]', 200),
        });

    final api = await StudentApiClient.signIn(
      apiBaseUrl: 'http://localhost:8080/api',
      username: 'student',
      password: 'password',
      client: mock,
    );

    await expectLater(
      api.loadLedger(),
      throwsA(
        isA<ApiException>().having(
          (failure) => failure.message,
          'message',
          'The server returned a list in an unexpected format.',
        ),
      ),
    );
    api.logout();
  });

  test('logout clears credentials and prevents further API requests', () async {
    var requests = 0;
    final mock = MockClient((_) async {
      requests++;
      return http.Response(
        jsonEncode({
          'username': 'student',
          'roles': ['ROLE_STUDENT'],
        }),
        200,
      );
    });

    final api = await StudentApiClient.signIn(
      apiBaseUrl: 'http://localhost:8080/api',
      username: 'student',
      password: 'password',
      client: mock,
    );
    api.logout();

    await expectLater(
      api.loadLedger(),
      throwsA(
        isA<ApiException>().having(
          (failure) => failure.message,
          'message',
          'You have been signed out. Sign in again.',
        ),
      ),
    );
    expect(requests, 1);
  });
}
