# Track 2 Department Financial Records, Collections, Attendance, and Reporting System

Track 2 is a department management system with a Spring Boot REST API, a Vue/Vuetify administrative web app, a PostgreSQL database, and a Flutter student portal. It supports student records, fines and payments, department funds and expenses, attendance, reports, user roles, student-account linking, and audit logs.

This is an academic development project, not a production-hardened financial system. LAN sharing and production deployment are not configured.

## Technology stack

| Area | Technology |
| --- | --- |
| Backend | Java 21, Spring Boot 3.4.5, Spring MVC, Spring Data JPA, Spring Security, Bean Validation |
| Database | PostgreSQL; the Track 2 development instance uses `127.0.0.1:55432` |
| API documentation | springdoc OpenAPI 2.8.6 and Swagger UI |
| Administrative web app | Vue 3, Vuetify 3, Vite, Axios |
| Student app | Flutter/Dart, Material 3, Android |
| Tests | JUnit 5, Spring Security Test, Flutter test |

## Features

- Role-based sign-in for Administrator, Treasurer, Officer, and Student accounts.
- Dashboard with payment, fund, unpaid-balance, and attendance summaries.
- Student records and student fines.
- Payment records, partial payments, balance checks, and append-only correcting entries.
- Department fund additions and expenses with balance checks.
- Attendance activities, manual/QR student-ID scanning, and attendance records.
- Student, finance, funds, and attendance reports.
- User account administration, one-to-one student-account linking, and audit logs.
- Student portal for a linked student's account summary, fines/payment history, attendance, profile, and student-ID QR code.

## Project structure

```text
backend/       Spring Boot API, configuration, and Maven tests
database/      PostgreSQL notes, student-link migration, and local backups
data/          Local PostgreSQL data files (private, not for submission)
frontend/      Vue/Vuetify administrative application
mobile/        Flutter student portal, Android platform, and Flutter tests
scripts/       Local Track 2 backend startup helper
tools/         Local JDK, Maven repository, and Android SDK (not for submission)
```

## Requirements

For a fresh development setup, install or provide:

- Java 21 and Apache Maven 3.9.x.
- PostgreSQL 18, configured for local-only development on port `55432`.
- Node.js 20 or newer and npm.
- Flutter 3.47.x and Dart 3.13.x.
- For Android APK builds: Android SDK Platform 36, Build Tools 36.0.0, NDK 28.2.13676358, and Android command-line tools. Android Studio and an emulator are optional.

The versions above match the current Track 2 workspace. Its copies of the JDK, Android SDK, and Maven dependency cache live under `tools/` and are intentionally excluded from source submissions. A new developer should install the required tools separately.

## PostgreSQL setup

Keep PostgreSQL bound to loopback; do not expose the database port to a LAN. The current development database is `track2_department` on `127.0.0.1:55432`, using the dedicated database role `track2_app`.

For a new local PostgreSQL instance:

1. Configure the instance to listen only on `127.0.0.1` and use port `55432`.
2. Create a dedicated login role and database, using a locally chosen strong password:

   ```sql
   CREATE ROLE track2_app LOGIN PASSWORD '<choose-a-local-password>';
   CREATE DATABASE track2_department OWNER track2_app;
   ```

3. Store the database URL, role, and password in the untracked `database/.env` file. Use the exact target below; the startup helper refuses a different database:

   ```text
   DATABASE_URL=jdbc:postgresql://127.0.0.1:55432/track2_department
   DATABASE_USER=track2_app
   DATABASE_PASSWORD=<choose-a-local-password>
   ```

4. The development Spring profile uses `ddl-auto=update` to create/update the schema. This is development-only behavior. The student-account link migration is at [database/migrations/20261029_add_student_account_link.sql](database/migrations/20261029_add_student_account_link.sql); apply it only to the intended database after taking a backup. Existing accounts are not automatically linked.

The current workspace contains a private PostgreSQL data directory and a pre-migration backup. Neither should be copied into a public repository or shared as source.

## Backend setup

Create `backend/.env` locally with the following keys. Choose unique local development passwords; never commit this file:

```text
ADMIN_PASSWORD=<local-admin-password>
TREASURER_PASSWORD=<local-treasurer-password>
OFFICER_PASSWORD=<local-officer-password>
STUDENT_PASSWORD=<local-student-password>
```

The workspace helper [scripts/start-backend.ps1](scripts/start-backend.ps1) checks the expected local database identity, loopback listener, and local tools before starting Spring Boot. In the current workspace, run it from PowerShell:

```powershell
.\scripts\start-backend.ps1
```

That helper expects the workspace JDK at `tools/jdk-21`, Maven at `C:\Maven\apache-maven-3.9.9\bin\mvn.cmd`, PostgreSQL client tools under `C:\Program Files\PostgreSQL\18\bin`, and the two local `.env` files described above. These paths are specific to this workstation.

For a different workstation, configure the same environment variables in the current terminal process and run Maven from `backend/`:

```powershell
$env:DATABASE_URL = 'jdbc:postgresql://127.0.0.1:55432/track2_department'
$env:DATABASE_USER = 'track2_app'
$env:DATABASE_PASSWORD = '<local-database-password>'
$env:ADMIN_PASSWORD = '<local-admin-password>'
$env:TREASURER_PASSWORD = '<local-treasurer-password>'
$env:OFFICER_PASSWORD = '<local-officer-password>'
$env:STUDENT_PASSWORD = '<local-student-password>'
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:SERVER_ADDRESS = '127.0.0.1'
$env:PORT = '8080'
Set-Location .\backend
mvn spring-boot:run
```

Keep secrets private and avoid pasting real values into shell history, chat, logs, or documentation. The development seeder creates only the four role accounts when absent; it does not create student or financial records.

The API listens on `127.0.0.1:8080` in the Track 2 startup script. Health check: <http://127.0.0.1:8080/api/health>.

## Vue administrative app setup

In a separate terminal:

```powershell
Set-Location .\frontend
npm ci
npm run dev
```

Open <http://localhost:5173>. Vite proxies `/api` requests to the local backend at `http://127.0.0.1:8080`. The backend's development CORS origin is `http://localhost:5173`.

To create the production web bundle:

```powershell
npm run build
```

There is no dedicated frontend unit-test script in `frontend/package.json`.

## Flutter student app setup

From `mobile/`:

```powershell
flutter pub get
flutter analyze
flutter test
```

The current Android build uses Flutter's configured Android SDK path in the untracked `mobile/android/local.properties`. On the original development machine, the SDK is installed at `tools/android-sdk`; that local SDK directory is not submitted.

For an Android emulator, the default API base URL is `http://10.0.2.2:8080/api`. To build the debug APK:

```powershell
flutter build apk --debug
```

Expected output: `mobile/build/app/outputs/flutter-apk/app-debug.apk`. The current workspace has a successful debug APK, but it is debug-signed and is not a Play Store/release package.

Flutter Web scaffolding exists, but browser/API integration requires a CORS origin allowed by the backend. The backend currently permits `http://localhost:5173`, not the Flutter web test port. Do not broaden CORS with a wildcard.

## OpenAPI and Swagger

When the backend is running locally:

- Swagger UI: <http://127.0.0.1:8080/swagger-ui.html>
- OpenAPI JSON: <http://127.0.0.1:8080/v3/api-docs>

These are local development URLs. Do not expose documentation or the API on a public network without an approved security configuration.

## API integration

The REST API base path is `/api`. Useful route groups include:

| Purpose | Routes |
| --- | --- |
| Health and signed-in identity | `GET /api/health`, `GET /api/me` |
| Students and balances | `/api/students` |
| Fines, payments, and corrections | `/api/finance/ledger` |
| Department funds and expenses | `/api/finance/funds` |
| Dashboard and reports | `/api/dashboard`, `/api/reports/**` |
| Attendance activities and records | `/api/attendance/**` |
| User accounts and student links | `/api/admin/users/**` |
| Audit log | `GET /api/admin/audit-logs` |
| Authenticated student's own data | `/api/students/me/**` |

Requests and responses use JSON. Authentication is HTTP Basic, and the backend enforces role permissions and student ownership. Do not embed credentials in a client, source file, QR code, or URL. Use HTTPS before sending credentials or accessing protected data across computers. LAN access is not currently enabled: the API and PostgreSQL are loopback-bound. Do not expose PostgreSQL.

The Vue app uses the same-origin `/api` path through its development proxy. The Flutter app accepts an API base URL at build/run time with `--dart-define=API_BASE_URL=...` or on its sign-in screen. Android emulator `10.0.2.2` refers to the development computer; it is not the correct address for a physical device or another computer. LAN sharing requires separately approved network binding, firewall, CORS, and HTTPS configuration.

### Roles

| Role | Access |
| --- | --- |
| Administrator | Dashboard, students, finance, funds/expenses, attendance, reports, user accounts, student links, and audit logs |
| Treasurer | Dashboard, read-only student records, finance, funds/expenses, and reports |
| Officer | Dashboard, read-only student records, attendance, and reports |
| Student | Own linked student summary, balance/ledger, and attendance only |

An unlinked Student account receives a setup response for student-only data until an administrator links it to an existing student record. No student records are supplied as demonstration data.

## Tests and verification

Run backend tests from the project root using the available Maven installation:

```powershell
Set-Location .\backend
mvn test
```

For the current workspace, the Maven repository may be set explicitly:

```powershell
mvn "-Dmaven.repo.local=..\tools\maven-repo" test
```

Frontend production build:

```powershell
Set-Location .\frontend
npm ci
npm run build
```

Flutter checks:

```powershell
Set-Location .\mobile
flutter pub get
flutter analyze
flutter test
```

The last recorded checks passed: 28 backend tests, 4 Flutter tests, Vue production build, and Android debug APK build. Browser smoke testing covered navigation across all 11 web pages and invalid login; no real student or financial records were created for testing.

## Limitations and remaining integration work

- LAN sharing is paused and not complete. The API currently listens on loopback; no LAN firewall rule or HTTPS endpoint is configured.
- The database currently has no student records and no linked Student accounts. Student personal data, a real student QR, and live student portal data cannot be demonstrated until approved records are available and linked.
- Financial and attendance flows have automated unit/service tests, but live record creation, balance changes, correction behavior, and camera-based QR scanning were not verified against populated production-like data.
- Browser API calls are cross-origin restricted to the configured development origin. Flutter Web currently needs a specifically approved CORS origin to call the API.
- The student QR contains a student ID for lookup; it is not an authentication credential or proof of attendance.
- HTTP Basic is suitable only for controlled local development. Do not use it over plain HTTP on a LAN or the Internet.
- The project is not production hardened or deployed. It has no production TLS setup, deployment configuration, or complete live integration-test suite.

## Submission and secret-handling checklist

- Keep `backend/.env`, `database/.env`, database data, database backups, local SDKs/tool caches, generated output, and `mobile/android/local.properties` out of source control.
- `backend/.env.example` contains placeholder values only and may be used as a reference; replace placeholders locally and never store real credentials in the example.
- Review the complete staged file list and scan for credentials before submitting.
- The current workspace is not initialized as a Git repository, so Git status and staged-file checks cannot be performed here. Initialize/clone the intended repository before making a submission; do not commit secrets, local database files, or generated artifacts.
- Do not describe LAN sharing or production deployment as complete.
