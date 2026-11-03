import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:department_student_portal/portal_app.dart';
import 'package:qr_flutter/qr_flutter.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  testWidgets('sign-in has no preset username or password', (tester) async {
    await tester.pumpWidget(
      const StudentPortalApp(apiBaseUrl: 'http://localhost:8080/api'),
    );

    expect(find.text('Student Portal'), findsOneWidget);
    expect(find.text('Sign in with your department student account.'),
        findsOneWidget);
    expect(find.text('student'), findsNothing);
    expect(find.text('API server URL'), findsOneWidget);
    expect(find.text('Username'), findsOneWidget);
    expect(find.text('Password'), findsOneWidget);
  });

  testWidgets('invalid credentials show a clear sign-in error', (tester) async {
    final client = MockClient((_) async => http.Response('', 401));
    await tester.pumpWidget(
      StudentPortalApp(
        apiBaseUrl: 'http://localhost:8080/api',
        clientFactory: () => client,
      ),
    );

    await tester.enterText(find.byKey(const Key('username')), 'student-one');
    await tester.enterText(find.byKey(const Key('password')), 'wrong-password');
    await tester.tap(find.text('Sign in'));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('login-error')), findsOneWidget);
    expect(
      find.text('Username or password is incorrect. Try again.'),
      findsOneWidget,
    );
    expect(find.text('Student Portal'), findsOneWidget);
  });

  testWidgets('attendance history shows server check-in time and status',
      (tester) async {
    final client = MockClient((request) async {
      return switch (request.url.path) {
        '/api/me' => http.Response(
            jsonEncode({
              'username': 'student-one',
              'roles': ['ROLE_STUDENT'],
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
              'unpaidBalance': 0,
              'attendance': {
                'total': 1,
                'present': 1,
                'late': 0,
                'absent': 0,
                'excused': 0,
              },
            }),
            200,
          ),
        '/api/students/me/ledger' => http.Response('[]', 200),
        '/api/students/me/attendance' => http.Response(
            jsonEncode([
              {
                'activityName': 'Department Assembly',
                'activityDate': '2026-10-26',
                'scannedAt': '2026-10-26T09:05:00',
                'status': 'PRESENT',
              },
            ]),
            200,
          ),
        _ => http.Response('Not found', 404),
      };
    });

    await tester.pumpWidget(
      StudentPortalApp(clientFactory: () => client),
    );
    await tester.enterText(find.byKey(const Key('username')), 'student-one');
    await tester.enterText(find.byKey(const Key('password')), 'password');
    await tester.tap(find.text('Sign in'));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Attendance'));
    await tester.pumpAndSettle();

    expect(find.text('Department Assembly'), findsOneWidget);
    expect(find.textContaining('Checked in 9:05 AM'), findsOneWidget);
    expect(find.text('Present'), findsOneWidget);

    await tester.tap(find.text('Profile'));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('My QR code'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('My QR code'));
    await tester.pumpAndSettle();
    expect(find.byType(QrImageView), findsOneWidget);
    expect(
      tester.widget<QrImageView>(find.byType(QrImageView)).semanticsLabel,
      'Student ID QR code for S-001',
    );
    expect(find.text('Student ID: S-001'), findsOneWidget);
  });

  testWidgets('fine and payment history includes correction entries',
      (tester) async {
    final client = MockClient((request) async {
      return switch (request.url.path) {
        '/api/me' => http.Response(
            jsonEncode({
              'username': 'student-one',
              'roles': ['ROLE_STUDENT'],
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
              'unpaidBalance': 100,
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
        '/api/students/me/ledger' => http.Response(
            jsonEncode([
              {
                'kind': 'FINE',
                'amount': 100,
                'description': 'Late arrival fine',
                'createdAt': '2026-10-25T10:00:00',
                'correction': false,
                'corrected': false,
              },
              {
                'kind': 'PAYMENT',
                'amount': 40,
                'description': 'Partial payment',
                'createdAt': '2026-10-26T10:00:00',
                'correction': false,
                'corrected': true,
              },
              {
                'kind': 'FINE',
                'amount': 40,
                'description': 'REVERSAL:PAYMENT:2:Test correction',
                'createdAt': '2026-10-27T10:00:00',
                'correction': true,
                'corrected': false,
              },
              {
                'kind': 'PAYMENT',
                'amount': 100,
                'description': 'REVERSAL:FINE:1:Test correction',
                'createdAt': '2026-10-28T10:00:00',
                'correction': true,
                'corrected': false,
              },
            ]),
            200,
          ),
        '/api/students/me/attendance' => http.Response('[]', 200),
        _ => http.Response('Not found', 404),
      };
    });

    await tester.pumpWidget(
      StudentPortalApp(clientFactory: () => client),
    );
    await tester.enterText(find.byKey(const Key('username')), 'student-one');
    await tester.enterText(find.byKey(const Key('password')), 'password');
    await tester.tap(find.text('Sign in'));
    await tester.pumpAndSettle();

    expect(find.text('₱100.00'), findsOneWidget);
    await tester.tap(find.text('Fines'));
    await tester.pumpAndSettle();
    expect(find.text('Late arrival fine'), findsOneWidget);
    expect(find.text('Balance correction'), findsOneWidget);
    expect(find.text('Correction'), findsOneWidget);

    await tester.tap(find.text('Payments'));
    await tester.pumpAndSettle();
    expect(find.text('Partial payment'), findsOneWidget);
    expect(find.text('Corrected'), findsOneWidget);
    expect(find.text('Balance correction'), findsOneWidget);
    expect(find.text('Correction'), findsOneWidget);
    expect(find.textContaining('REVERSAL:'), findsNothing);

    await tester.tap(find.text('Profile'));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('Log out'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Log out'));
    await tester.pumpAndSettle();
    expect(find.text('Sign in'), findsOneWidget);
    expect(find.text('student-one'), findsNothing);
  });
}
