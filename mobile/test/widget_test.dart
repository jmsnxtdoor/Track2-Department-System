import 'package:department_student_portal/portal_app.dart';
import 'package:flutter_test/flutter_test.dart';

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
}
