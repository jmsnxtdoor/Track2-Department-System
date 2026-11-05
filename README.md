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

The only API route exposed to a peer laptop is a dedicated HTTPS listener for `GET /api/integration/v1/summary`. It returns a non-personal status summary and requires the receiving laptop's `X-API-Key`. The main Spring Boot listener and PostgreSQL remain bound to loopback; the HTTPS Vite server is loopback-only as well. The LAN listener is disabled unless explicitly enabled with a private bind address, a PKCS12 TLS certificate, and a Windows Firewall rule for one peer IPv4 address. It returns 404 for every other path, method, or query string on that listener. Do not expose the general API, Vite, Swagger, or PostgreSQL to the LAN.

Administrators can use the web app's **External API** page to make a one-time, read-only GET request to an HTTPS endpoint that returns JSON. The request is made by the local Spring Boot backend; the remote API does not need browser CORS access. An optional Bearer token is sent only to that endpoint, is cleared after the request, and is not saved. This does not import, store, or synchronize the external dataset; mapping or syncing requires that system's API documentation.

The Administrator **External API** page makes a one-time server-side GET to an exactly allowlisted HTTPS URL. Public endpoints stay in `EXTERNAL_API_ALLOWED_ENDPOINTS`; peer LAN URLs must be explicitly placed in `EXTERNAL_API_ALLOWED_PRIVATE_ENDPOINTS`. The LAN opt-in accepts only private IPv4/IPv6 unique-local resolutions and still uses normal Java certificate-chain and hostname verification. Do not use real student or financial data in demonstrations.

The remote GET is made by Spring Boot, not directly by the browser, so the remote peer does not need browser CORS access. The browser communicates only with its same-origin local backend.

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

The Vue development server is available only at `https://127.0.0.1:5173/`; it is not served to LAN clients. The optional Spring Boot integration connector binds only the configured private Wi-Fi IPv4 address, and exposes only the API summary route. The startup helper keeps the main API listener on `127.0.0.1` and verifies the peer-scoped firewall rule. PostgreSQL remains loopback-only.

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
| Shared system integration summary | `GET /api/integration/v1/summary` |
| Administrator external API proxy | `POST /api/admin/external-api/fetch` |

Requests and responses use JSON. Authentication is HTTP Basic, and the backend enforces role permissions and student ownership. Do not embed credentials in a client, source file, QR code, or URL. Use HTTPS before sending credentials or accessing protected data across computers. Only the dedicated non-personal integration summary is eligible for the narrowly scoped LAN listener; do not expose PostgreSQL.

### Shared API-key demonstration

The provider endpoint is separate from application login and requires `X-API-Key`, configured locally with `TRACK2_INTEGRATION_API_KEY`. It returns only a non-personal service summary. The Administrator External API page sends GET requests through Spring Boot; it supports `X-API-Key` and Bearer authentication, exact endpoint allowlisting, HTTPS for remote systems, a 10-second request timeout, and a 1 MB JSON response limit. Add approved remote HTTPS endpoints to the comma-separated `EXTERNAL_API_ALLOWED_ENDPOINTS` setting in the ignored `backend/.env.integration.local`. Do not put credentials in URLs.

The provider endpoint is separate from application login. Each laptop retains its own independently generated provider key; never replace or reuse the other laptop's key. The Administrator External API page makes GET requests through Spring Boot, which enforces exact endpoint allowlisting, HTTPS for remote systems, a 10-second request timeout, and a 1 MB JSON response limit. The local mock allowlist and mock flow remain supported.

#### Two-laptop setup (Windows)

Perform these steps only on a trusted Wi-Fi network that allows peer-to-peer clients. Do not use a guest network or public hotspot. The partner's computer must be available to obtain its current private IPv4 address and transfer its public CA certificate and API key securely.

1. Laptop A's `track2-a.lan` certificate is already prepared locally; its public CA certificate is `%LOCALAPPDATA%\Track2-HTTPS\track2-a-lan-ca.cer`. On laptop B, prepare its independent certificate:

   ```powershell
   .\scripts\new-track2-lan-certificate.ps1 -HostName track2-b.lan
   ```

   The command creates a local CA and a hostname-specific TLS certificate in `%LOCALAPPDATA%\Track2-HTTPS`, writes the PKCS12 path and a random password to the Git-ignored, ACL-protected `backend/.env.integration.local`, and leaves the listener disabled. For a fresh laptop A, run it once with `-HostName track2-a.lan`. Share only the generated `*-ca.cer` public CA certificate and its independently confirmed SHA-256 fingerprint. Never share the `.pfx`, its password, the CA private key, or any database/application credentials.

2. On each receiving laptop, verify the peer CA fingerprint with the peer over a separate trusted channel, then trust that CA in Windows and in the Java runtime used by Spring Boot. Replace the sample path/alias as needed; use an alias unique to the peer:

   ```powershell
   Get-FileHash -Algorithm SHA256 '<path-to-peer-ca.cer>'
   Import-Certificate -FilePath '<peer-ca.cer>' -CertStoreLocation Cert:\CurrentUser\Root
   $javaHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { (Resolve-Path '.\tools\jdk-21').Path }
   & "$javaHome\bin\keytool.exe" -importcert -trustcacerts -alias track2-peer-ca -file '<peer-ca.cer>' -keystore "$javaHome\lib\security\cacerts"
   ```

   For this workspace's startup helper, `JAVA_HOME` resolves to `tools\jdk-21` unless overridden. `keytool` will prompt for the trust-store password and confirmation; retain existing root certificates. Do not use trust-all settings, `curl -k`, or disable hostname checks. To remove the Java trust entry after the demo, run `& "$javaHome\bin\keytool.exe" -delete -alias track2-peer-ca -keystore "$javaHome\lib\security\cacerts"` and enter the trust-store password. Remove the Windows root entry using the verified peer certificate thumbprint.

3. Exchange only the laptops' current private Wi-Fi IPv4 addresses and add a host entry on each receiving laptop (edit `C:\Windows\System32\drivers\etc\hosts` as Administrator):

   ```text
   <A_WIFI_PRIVATE_IPV4> track2-a.lan
   <B_WIFI_PRIVATE_IPV4> track2-b.lan
   ```

   Find the Wi-Fi address with `Get-NetIPAddress -AddressFamily IPv4`; do not use `127.0.0.1`, `169.254.*`, a public address, or a guessed address. The hostname in the URL must exactly match the TLS certificate.

4. On each laptop, enable its listener with its own Wi-Fi address and the other laptop's address. Run PowerShell as Administrator because the command installs a firewall rule scoped to exactly that local address, TCP port 8443, and peer address:

   ```powershell
   .\scripts\enable-track2-lan-integration.ps1 -BindAddress <THIS_WIFI_PRIVATE_IPV4> -PartnerAddress <PEER_WIFI_PRIVATE_IPV4>
   ```

   This does not stop or restart services. Do not start or restart Spring Boot without approval. The normal launcher still binds the application API to `127.0.0.1:8080`; when LAN integration is enabled it adds only the separately bound TLS connector. The launcher refuses startup if the exact peer firewall rule, certificate, or private bind address is missing.

5. Add the peer's exact provider URL to the local `EXTERNAL_API_ALLOWED_PRIVATE_ENDPOINTS` setting in `backend/.env.integration.local` (preserve all other entries and existing keys):

   - On laptop A: `https://track2-b.lan:8443/api/integration/v1/summary`
   - On laptop B: `https://track2-a.lan:8443/api/integration/v1/summary`

   Configure each laptop's current `TRACK2_INTEGRATION_API_KEY` as the key the other laptop uses to fetch its summary. Exchange these keys out of band; never place them in a URL, source file, command history, or shared chat. Restart Spring Boot only with the owner's approval so the launcher loads the new configuration.

6. For a one-way check from A to B, sign in as Admin on A, open **External API**, enter A's exact allowlisted URL for B, choose **X-API-Key**, paste B's provider key, and select **Get JSON data**. Repeat in the reverse direction from B using A's URL and key. Each response must identify `NON_PERSONAL_SUMMARY`; the fetch is read-only and does not create, change, or synchronize database records. Confirm `https://127.0.0.1:5173/` still opens locally on each laptop.

To disable access, set `TRACK2_LAN_API_ENABLED=false`, remove the **Track2 read-only integration HTTPS** firewall rule (`Remove-NetFirewallRule -DisplayName 'Track2 read-only integration HTTPS'`), remove the peer endpoint from the private allowlist, and restart Spring Boot with approval. Remove the peer's hosts entry and imported CA when finished. If either Wi-Fi address changes, update the hosts entry, private allowlist, bind/firewall settings, and restart only with approval.

Run the local loopback-only demonstration from the project root:

```powershell
.\scripts\start-api-integration-demo.ps1
.\scripts\start-backend.ps1
.\scripts\test-api-integration-demo.ps1
```

The first command generates independent cryptographically secure demo keys into the Git-ignored `backend/.env.integration.local`; it does not display or copy keys. It starts the mock at `http://127.0.0.1:8091`; its JSON is labeled `DEMONSTRATION_ONLY` and is not real integrated data. The backend launcher loads the Track 2 shared key and exact loopback endpoint allowlist from that local file. If Spring Boot is already running, restart only Spring Boot after setting up the demo configuration. Do not expose the mock or database to the LAN.

To copy the mock key only when needed, run `.\scripts\manage-api-integration-keys.ps1 -Mode CopyExternalKey`; the value is sent to the clipboard and is never printed. In the Admin UI, open **External API**, enter `http://127.0.0.1:8091/v1/demo`, select **X-API-Key**, paste the key, and select **Get JSON data**. The response labels the upstream HTTP status separately from errors returned by the Track 2 backend. The test script checks correct, incorrect, and missing keys against both systems and confirms the mock can retrieve Track 2's non-personal summary. It does not create or modify application records.

The two demonstration keys were exposed and must be rotated. Prepare a replacement pair without disrupting a running demonstration with `.\scripts\manage-api-integration-keys.ps1 -Mode Prepare`. For activation, arrange a maintenance window, stop the mock and Spring Boot manually, then run `.\scripts\manage-api-integration-keys.ps1 -Mode Activate`; it refuses to replace the local configuration while either API port is listening. Start the mock and backend again, then copy the new external key explicitly. No process is stopped by these commands.

Financial write requests require an `idempotencyKey` UUID in the JSON body for fines, payments, fund entries, expenses, and corrections. Retry an uncertain submission with the same key and unchanged details; start a separate transaction with a new key. The Vue app retains pending keys in browser storage, scoped to the signed-in username, until the request succeeds. Apply `backend/migrations/20261104_financial_idempotency.sql` to any target database before deploying the backend change; migrations are not run automatically.

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
