# Track 2 Student Portal

The Flutter app signs students in through the existing Spring Boot API. It
shows the linked student profile, account-level unpaid balance, fine records,
payment history, attendance records, and a QR code containing the student's
existing student ID. Credentials and the HTTP Basic authorization value stay
in memory and are cleared on logout.

## Run locally

Install the Flutter Android toolchain, then from this directory run:

```powershell
flutter pub get
flutter analyze
flutter test
flutter run
```

The default API URL is `http://10.0.2.2:8080/api` for an Android emulator. To
use a different server, set the build-time value or edit the API server URL on
the sign-in page:

```powershell
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080/api
```

For a physical phone, use the computer's reachable LAN address instead of
`10.0.2.2`, and allow the phone to reach the backend through the local network
and firewall. The debug Android manifest permits cleartext HTTP for local
development only. Use HTTPS for deployed systems.

## Student account setup

Apply the database migration described in `../database/README.md`. An
administrator must then link each student account to its existing student
record in the web system's **User Accounts** page. Unlinked accounts cannot
read personal student data.

Payments reduce the student's total balance; the system does not assign
payments to individual fines. The app therefore shows payment status at the
account level and does not claim that a particular fine is paid.

The QR contains only the linked student ID expected by the existing attendance
scanner. It is not an authentication credential and does not prove attendance
by itself.
