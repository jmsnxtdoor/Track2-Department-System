# Track 2 Department Financial Records, Collections, Attendance, and Reporting System

Track 2 is a department management system with a Spring Boot REST API, a Vue/Vuetify administrative web app, a PostgreSQL database, and a Flutter student portal. It supports student records, fines and payments, department funds and expenses, attendance, reports, user roles, student-account linking, and audit logs.

This is an academic development project, not a production-hardened financial system. The backend is loopback-only; the startup helper rejects non-loopback binds, and no firewall rules or production deployment are configured.

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
- Role-filtered dashboard with authorized finance or attendance summaries.
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
tools/         Local JDK and Maven repository; optional Android SDK (not for submission)
```

## Requirements

For a fresh development setup, install or provide:

- Java 21 and Apache Maven 3.9.x.
- PostgreSQL 18, configured for local-only development on port `55432`.
- Node.js 20 or newer and npm.
- Flutter 3.47.x and Dart 3.13.x.
- For Android APK builds: Android SDK Platform 36, Build Tools 36.0.0, NDK 28.2.13676358, and Android command-line tools. Android Studio and an emulator are optional.

The versions above match the current Track 2 workspace. Local copies of the JDK, Android SDK, and Maven dependency cache are excluded from source submissions. This workspace has Android SDK components under `tools/android-sdk`; a new developer should install the required tools separately.

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

The helper prefers the workspace JDK at `tools/jdk-21`, Maven at `C:\Maven\apache-maven-3.9.9\bin\mvn.cmd`, and PostgreSQL client tools under `C:\Program Files\PostgreSQL\18\bin`. If those are absent, it checks `JAVA_HOME`/`PATH` for Java and Maven and uses the newest PostgreSQL client under `C:\Program Files\PostgreSQL`. The two local `.env` files described above are still required.

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

The API listens on `127.0.0.1:8080` by default. Health check: <http://127.0.0.1:8080/api/health>.

### Optional private-LAN API access

The REST API already exposes student, attendance, finance, and report endpoints. Swagger UI is at `/swagger-ui.html`, and the OpenAPI JSON is at `/v3/api-docs`. API endpoints use HTTP Basic authentication and the application's role-based permissions; use an account with only the permissions the integration needs.

Administrators can use the web app's **External API** page to make a one-time, read-only GET request to an HTTPS endpoint that returns JSON. Requests are made directly by the browser, so the external API must allow the web app's origin through CORS. An optional Bearer token is sent only to that endpoint, is cleared after the request, and is not saved. This does not import, store, or synchronize the external dataset; mapping or syncing requires that system's API documentation.

For HTTPS LAN testing, expose only the Vue/Vite HTTPS server to the trusted network. Its `/api` proxy forwards to `http://127.0.0.1:8080`; Spring Boot and PostgreSQL stay bound to loopback. Do not pass a LAN address to `-ServerAddress`. No Windows Firewall or certificate trust changes are made by the startup helper.

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

For an Android emulator with the local backend running:

```powershell
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080/api
```

For Flutter Web on the same computer, set `API_BASE_URL` to the local backend URL and configure an exact CORS origin suitable for that client. The Vue HTTPS development setup uses its same-origin `/api` proxy and does not send browser requests directly to Spring Boot.

The Android SDK path is configured in the untracked `mobile/android/local.properties`; it is machine-specific and is not submitted.

The app's default API base URL is `http://10.0.2.2:8080/api`, which works for an Android emulator when the backend is listening on the development computer. To build a debug APK after Android SDK setup:

```powershell
flutter build apk --debug
```

Expected output: `mobile/build/app/outputs/flutter-apk/app-debug.apk`. This is a debug-signed package for local testing, not a release or Play Store package. A release build requires an approved signing key and release signing configuration; do not use the debug key for distribution.

Vue and Flutter Web have both been verified against the same local Spring Boot API and PostgreSQL database using the allowed `http://localhost:5173` origin. If you use another browser origin, configure that exact origin; do not use a wildcard.

### Android SDK setup and APK verification

The Android SDK is not included in this repository. This workspace has the required SDK packages under `tools/android-sdk`; a fresh checkout needs Android Studio or the Android command-line tools and the following SDK Manager packages:

- Android SDK Platform 36.
- Android SDK Build-Tools 36.0.0.
- Android NDK 28.2.13676358.
- Android SDK Command-line Tools (latest).

Then configure Flutter to use the installed SDK, accept the Android SDK licenses, and verify `flutter doctor -v` reports the Android toolchain as available:

```powershell
flutter config --android-sdk "<path-to-Android-SDK>"
flutter config --jdk-dir "<path-to-JDK-21>"
flutter doctor --android-licenses
flutter doctor -v
```

The SDK and JDK paths are machine-specific. Generate/update the ignored `mobile/android/local.properties` for the local machine; do not commit it. The current workspace's `local.properties` points to `tools/android-sdk`, and Flutter is configured to use that SDK and `tools/jdk-21`. Android Studio itself is optional when the command-line tools and required SDK packages are installed. Installation or download should be approved before proceeding.

After SDK setup, from `mobile/`, build the local debug APK with the intended API endpoint embedded:

```powershell
flutter build apk --debug --dart-define=API_BASE_URL=https://<approved-api-host>/api
```

Use the emulator URL `http://10.0.2.2:8080/api` only for local emulator testing. Plain HTTP is not appropriate for sending HTTP Basic credentials over a network.

### Computer and phone connectivity

The Vue HTTPS development server can serve the UI to a trusted LAN client; its `/api` proxy reaches Spring Boot over loopback. The startup helper and application default keep Spring Boot bound to `127.0.0.1`, and PostgreSQL remains loopback-only. Do not access the backend directly from another device.

External clients such as Flutter must use a separately approved HTTPS API endpoint; the local development backend is not directly reachable from other devices. Android native HTTP requests do not use browser CORS, but still require network routing and valid TLS. Do not expose PostgreSQL port 55432, use wildcard CORS, or send Basic credentials over plain HTTP. This repository does not include a production reverse proxy, firewall rules, or public deployment configuration.

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

Financial write requests require an `idempotencyKey` UUID in the JSON body for fines, payments, fund entries, expenses, and corrections. Retry an uncertain submission with the same key and unchanged details; start a separate transaction with a new key. The Vue app retains pending keys in browser storage, scoped to the signed-in username, until the request succeeds. Before deploying the backend change, apply `backend/migrations/20261104_financial_idempotency.sql` to the intended database; the migration is not run automatically, and the active database was not changed during implementation.

The Vue app uses the same-origin `/api` path through its development proxy. The Flutter app accepts an API base URL at build/run time with `--dart-define=API_BASE_URL=...` or on its sign-in screen. Android emulator `10.0.2.2` refers to the development computer; it is not the correct address for a physical device or another computer. LAN sharing requires separately approved network binding, firewall, CORS, and HTTPS configuration.

### Roles

| Role | Access |
| --- | --- |
| Administrator | Dashboard, students, finance, funds/expenses, attendance, reports, user accounts, student links, and audit logs |
| Treasurer | Read-only student records; manage fines, payments, funds, and expenses; financial dashboard metrics and finance/fund reports only |
| Officer | Read-only student records; manage attendance activities and records; attendance dashboard metrics and attendance reports only; no student ledger/balance access |
| Student | Own linked student summary, balance/ledger, and attendance only |

Treasurers and Officers cannot access user management. Dashboard responses and report routes are filtered by role: the Treasurer does not receive attendance metrics/reports, and the Officer does not receive financial metrics, finance/fund reports, or another student's ledger/balance. Students use only linked-record `/api/students/me/**` endpoints and cannot access staff dashboards or reports. Administrator access includes all current modules; no hard-delete endpoints are implemented.

An unlinked Student account receives a setup response for student-only data until an administrator links it to an existing student record. The current local development database contains one fictional DEMO student linked to the development Student account. This record is local database data, is not seeded by the application, and is not included in a fresh source checkout.

### Local HTTPS CORS configuration

The backend startup script reads `backend/.env.cors.local`, which is ignored by Git. Create it with a comma-separated exact-origin allowlist, for example:

```text
CORS_ORIGIN=https://127.0.0.1:5173,https://<current-private-LAN-IPv4>:5173
```

Replace the placeholder with the laptop's current private IPv4 address. If that address changes, update this local file; do not add the machine-specific address to application source. The startup script accepts only HTTPS IPv4 origins on port 5173 that are either `127.0.0.1` or RFC1918 private addresses. It rejects wildcard and non-private origins. Run `.\scripts\start-backend.ps1` without `-ServerAddress` to keep Spring Boot bound to loopback; the Vite proxy forwards `/api` requests to that local backend. PostgreSQL remains on loopback.

### Rotating the development Student password

To rotate the existing development Student account password without changing its student link:

1. Sign in to the Vue Admin interface and open **Users & Roles**.
2. Edit the existing `student` account and enter the new password in the password field. Leave its role, enabled state, and linked Student selection unchanged.
3. Save, confirm the Linked Student column still shows the DEMO record, and test a new sign-in using the new password.
4. Update `STUDENT_PASSWORD` in the ignored local `backend/.env` file to match. Do not paste the value into chat, source code, or documentation.

Changing `backend/.env` alone does not reset an existing account password: the development account seeder creates an account only when it is missing.

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

The PostgreSQL concurrency regression (`FinancialConcurrencyIntegrationTest`) is opt-in. It creates and drops its schema and tests concurrent payments, expenses, attendance scans, and student-account links, so point it only at a disposable, isolated test database—not the development database:

```powershell
mvn "-Dtrack2.test.jdbcUrl=jdbc:postgresql://127.0.0.1:55434/track2_concurrency_test" `
    "-Dtrack2.test.username=postgres" `
    "-Dtrack2.test.password=" test
```

Without `track2.test.jdbcUrl`, the concurrency integration test is skipped; the remaining Maven tests still run.

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

Latest Phase 5 verification: 39 Maven tests passed (including 6 PostgreSQL integration tests against disposable PostgreSQL), 10 Flutter tests passed, Flutter analysis was clean, the Flutter Web build passed, 2 Vue unit tests passed, and all 12 Playwright E2E tests passed. In Phase 6, `flutter doctor -v` verified the Android toolchain and `flutter build apk --debug --no-pub` successfully built a debug APK. These checks used fictional data and a disposable database. No Android emulator or physical Android device was available; the debug APK is not a release package. See `runtime-validation-report.md` for phase-specific evidence and limits.

## Limitations and remaining integration work

- The Vite HTTPS development proxy is configured for local LAN testing, but this task does not change firewall rules or certificate trust settings on another laptop.
- The current local development database may contain the fictional DEMO student; a fresh database/source checkout has no demonstration student records. Do not submit the local database or its backups.
- Financial, fund, attendance, reporting, and student portal workflows have passed automated disposable-PostgreSQL and browser tests as described in the runtime report. These results do not replace deployment or device certification.
- Physical Android execution and camera-based QR scanning have not been tested. Automated QR payload/API tests do not certify camera capture.
- Browser API calls are restricted to the exact HTTPS origins in the ignored local CORS configuration. Other hosts or ports require a deliberate configuration update.
- The student QR contains a student ID for lookup; it is not an authentication credential or proof of attendance.
- HTTP Basic is suitable only for controlled local development. Do not use it over plain HTTP on a LAN or the Internet.
- The project is not production hardened or deployed. Android debug APK generation passed, but the Android release build currently uses debug signing; no production TLS setup, reverse proxy, LAN firewall rules, or deployment automation is configured.

## Submission and secret-handling checklist

- Keep `backend/.env`, `database/.env`, database data, database backups, local SDKs/tool caches, generated output, and `mobile/android/local.properties` out of source control.
- `backend/.env.example` contains placeholder values only and may be used as a reference; replace placeholders locally and never store real credentials in the example.
- Review the complete staged file list and scan for credentials before submitting.
- The project is connected to [the Track2-Department-System GitHub repository](https://github.com/jmsnxtdoor/Track2-Department-System). Clone it before making changes on another workstation. The current `main` branch is synchronized with `origin/main`; verify Git status and review the exact staged file list before future commits.
- Do not describe LAN sharing or production deployment as complete.
