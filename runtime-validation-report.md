# Runtime Validation Report: Fines, Payments, and Collections

**Generated:** 2026-11-02
**Target:** `D:\Track2-Department-System`

## Summary

| Step | Status | Exit code | Details |
|---|---|---:|---|
| Startup | PASS | 0 | Backend, Vue, and Flutter Web ran on loopback against the disposable PostgreSQL cluster. |
| Backend tests | PASS | 0 | 34 tests passed; 0 failures, errors, or skips. The PostgreSQL concurrency/rollback class ran 2 tests. |
| Frontend unit tests | PASS | 0 | 2 tests passed. |
| Frontend production build | PASS | 0 | `vite build` completed successfully. |
| Browser/API E2E | PASS | 0 | 3 Playwright journeys passed, including Vue UI, authenticated API, and Flutter Web. |
| Flutter analysis/tests | PASS | 0 | No analyzer issues; 4 Flutter tests passed. |
| Isolated database cleanup | PASS | 0 | PostgreSQL on port 55436 and the temporary cluster were stopped and removed. |

**Overall:** PASS for the tested financial workflows. This does not certify physical Android behavior or untested export/printing paths.

## Environment and Isolation

- Used a fresh local PostgreSQL 18 cluster on port `55436`, database `track2_financial_e2e`.
- The isolated Spring Boot, Vue, and Flutter Web instances used loopback ports `8082`, `5175`, and `8083`.
- Test role credentials were supplied through environment variables and were not saved in test source.
- The development database listener on port `55432` remained available; no test configuration targeted it and no writes were made to it.
- The isolated database, application services, and generated Playwright result directory were cleaned up after validation.
- Docker was unavailable; the database tier still used real PostgreSQL, not an in-memory substitute. Node.js, Playwright, and Chromium were available.

## Feature Results

| Feature | Test performed | Status | Evidence | Fix needed |
|---|---|---|---|---|
| Fine creation and persistence | Admin created a fine through the Vue page; ledger read-back and page reload verified it remained present. | PASS | `POST /api/finance/ledger` returned 201; the test confirmed one POST despite two immediate form submissions. | Duplicate-submit guard added to the Vue ledger form. |
| Partial payment | Treasurer recorded PHP 40 against a PHP 100 fine through Vue. | PASS | `POST /api/finance/ledger` returned 201; balance remained PHP 60. |
| Dashboard and reports | Compared dashboard collections/unpaid values, unpaid-balance row, and finance report against ledger data. | PASS | Collection and balance deltas matched PHP 40 and PHP 60; report showed the fine and payment. |
| Database persistence | Queried the disposable PostgreSQL database directly for the latest fictional student records. | PASS | Two ledger rows: PHP 100 fine, PHP 40 payment, PHP 60 outstanding. |
| Student portal API | Read summary and ledger as a linked test student account. | PASS | Portal balance and fine/payment history matched the Vue and database results. |
| Invalid amount/student/reference | Submitted zero, negative, nonexistent-student, and duplicate-reference requests. | PASS | Expected 400/404/409 statuses and ProblemDetail response details were asserted. |
| Overpayment and cleared balance | Attempted payment above balance, paid the remaining balance, then attempted another payment. | PASS | Overpayments were rejected with HTTP 400 and `Payment exceeds unpaid balance.` |
| Reversal/correction totals | Reversed a full payment, partial payment, and fine; recomputed balance and report totals. | PASS | Final test-student balance was zero and net collection totals returned to baseline. |
| Concurrent payment integrity | Sent four simultaneous PHP 60 payment requests against a PHP 100 balance; also ran PostgreSQL-backed concurrent tests over 10 rounds. | PASS | Exactly one payment succeeded; three were rejected; remaining balance was PHP 40. |
| Transaction rollback and audit | Forced a transaction failure after ledger/audit writes; checked persisted rows and audit records. Also checked successful concurrent-payment audit rows. | PASS | Rollback left neither the ledger row nor its audit entry; one successful concurrent payment had one matching audit entry. |
| Authorization | Checked unauthenticated, Officer, Student, Admin, and Treasurer financial access. | PASS | Unauthenticated request returned 401; Officer/Student writes returned 403; authorized Admin/Treasurer flows succeeded. |
| Flutter Web | Signed in with the linked test student and viewed the balance, fine, and payment history in the browser. | PASS | Displayed PHP 60 balance, PHP 100 fine, and PHP 40 payment. |
| Physical Android | No physical device or emulator was used. | NOT TESTED | Requires a device/emulator. |
| CSV export and printing | No export or print action was exercised in this phase. | NOT TESTED | Separate report-export/print verification remains. |

## Findings and Changes

1. **Duplicate financial form submissions could race.** The Vue ledger submit handler had no in-flight guard. Two rapid submits could create duplicate requests, with a second payment potentially receiving a legitimate overpayment 400 after the first succeeded. Added a submission guard and disabled/loading state in `frontend/src/App.vue`. The Playwright regression test dispatches two immediate form submissions and asserts exactly one POST.
2. **The reported intermittent navigation-time “Bad Request” was not reproduced.** The authenticated Vue workflow navigated through Student Fines, Payments, Dashboard, Unpaid Balances, and Reports with no API response at or above 400. Expected validation failures were separately verified with their actual status and response details. The historical incident has no captured request trace, so its original cause is not established.
3. **Added repeatable browser/API coverage** in `frontend/e2e/` for persistence, payment validation, corrections, role restrictions, concurrency, student portal consistency, and Flutter Web. Playwright is wired through `npm run test:e2e` and `npm run test:all`.
4. **Expanded PostgreSQL integration coverage** in `backend/src/test/java/edu/department/portal/FinancialConcurrencyIntegrationTest.java` to assert audit consistency and rollback behavior.

## Test Evidence

- `mvn test -Dtrack2.test.jdbcUrl=jdbc:postgresql://127.0.0.1:55436/track2_financial_e2e -Dtrack2.test.username=postgres -Dtrack2.test.password=` — exit code 0; 34 passed, 0 failed, 0 skipped. The opt-in PostgreSQL test class executed 2 tests.
- `npm test` — exit code 0; 2 passed.
- `npm run build` — exit code 0.
- `npm run test:e2e` — exit code 0; 3 passed. Vue API responses for the valid fine/payment POSTs were 201; no 4xx/5xx occurred in the successful Vue navigation flow.
- `flutter analyze` — exit code 0; no issues.
- `flutter test` — exit code 0; 4 passed.
- Direct SQL read-back in the disposable database confirmed the PHP 100 / PHP 40 / PHP 60 scenario.
- `git diff --check` passed. No commits or pushes were made; existing README and source changes were preserved.

## Verdict

```text
environment:
  docker: UNAVAILABLE — local Docker infrastructure was not used
  node: AVAILABLE
  playwright: AVAILABLE — Chromium installed
  infra-tier: PRIMARY(real PostgreSQL) — isolated local PostgreSQL cluster on port 55436
  browser-tier: PRIMARY(Playwright)
startup: PASS — loopback backend, Vue, and Flutter Web responded; backend protected route returned expected unauthenticated status
integration: PASS — exit_code: 0, passed: 34, failed: 0, skipped: 0; PostgreSQL concurrency/rollback tests: 2 passed
e2e: PASS — exit_code: 0, passed: 3, failed: 0; Vue, API, and Flutter Web financial flows
overall: PASS — tested financial workflows were consistent across the UI, API, and isolated PostgreSQL
```

## Department Funds and Expenses

**Generated:** 2026-11-02
**Environment:** Disposable PostgreSQL on `127.0.0.1:55436`; isolated Spring Boot on `127.0.0.1:8082`; Vue on `127.0.0.1:5175`; Flutter Web on `127.0.0.1:8083`. The development database on port `55432` was not used.

### Results

| Feature | Test performed | Status | Evidence |
|---|---|---|---|
| Fund income and expense UI | Added PHP 1,000 income, then recorded PHP 250 and PHP 150 expenses in Vue; refreshed and verified persistence. | PASS | Playwright journey `funds-00-ui-workflow.spec.js`; database rows reconcile to PHP 600 for these three entries. |
| Validation and repeat submission | Exercised invalid amounts/records and in-flight submission protection. | PASS | Fund browser journey asserted validation; UI disables duplicate submission while a request is pending. |
| Expense balance and concurrency | Submitted concurrent PHP 600 expenses against PHP 1,000 available; verified only one succeeded. | PASS | `funds-authorization-and-concurrency.spec.js`; the separate PostgreSQL test also serializes mutations on an initially empty fund ledger. |
| Corrections and reversals | Reversed fund income and expense entries and checked the resulting balances and report. | PASS | `funds-corrections-and-report.spec.js`; database retains append-only reversal entries. |
| Reports, audit, CSV, and print | Checked fund report totals, audit history, CSV download, and print invocation in browser. | PASS | Fund corrections/report Playwright journey. |
| Role restrictions | Checked authorized and unauthorized fund mutations. | PASS | Fund authorization Playwright journey returned the expected authorization outcomes. |
| Database persistence and audit | Queried fund ledger and audit rows directly after browser journeys. | PASS | `track2_funds_e2e` contained 9 fund entries and 7 fund creation audit rows; correction journey asserted reversal audit records. |
| Flutter fund operations | The student Flutter portal does not expose department fund administration. | NOT TESTED | Not applicable to this module; Flutter Web was exercised by the existing student financial regression journey. |
| Physical Android | No device or emulator was used. | NOT TESTED | No claim of physical-device verification. |

### Changes and Findings

- Fixed a fund-ledger concurrency gap: row locks cannot serialize competing updates when the ledger has no rows. Fund creation and reversal now acquire a PostgreSQL transaction-scoped advisory lock before locking existing ledger rows.
- Added PostgreSQL coverage for empty-ledger serialization and transactional rollback of fund and audit writes.
- Added a Vue in-flight guard/loading state for fund submissions.
- Updated the existing Flutter Web regression journey to wait for `domcontentloaded`; the debug web server served an interactive app but did not complete Playwright's default `load` wait. The journey then completed successfully.
- The first final E2E attempt lacked test-only Playwright password environment variables; a subsequent attempt exposed the Flutter debug-server navigation wait. Both were test-environment issues, corrected without changing authorization or application behavior.

### Verification

- Backend: `mvn test` with `track2.test.jdbcUrl=jdbc:postgresql://127.0.0.1:55436/track2_funds_integration` — **35 passed, 0 failures, 0 errors, 0 skipped** (exit code 0). `FinancialConcurrencyIntegrationTest` ran **3** PostgreSQL-backed tests.
- Frontend unit tests: `npm test` — **2 passed** (exit code 0).
- Frontend production build: `npm run build` — passed (exit code 0).
- Browser/API: `npm run test:all` — **6 Playwright journeys passed**, including all **3 fund-specific journeys** and the existing Vue/API/Flutter Web financial regression journey (exit code 0).
- Direct SQL reconciled the specified fund entries: PHP 1,000.00 opening income − PHP 250.00 − PHP 150.00 = **PHP 600.00**. After the separate concurrency and correction scenarios, the complete disposable fund ledger netted PHP 400.00.
- `git diff --check` passed. No commits or pushes were made.

```text
environment:
  docker: UNAVAILABLE — local disposable PostgreSQL used instead of Docker
  node: AVAILABLE
  playwright: AVAILABLE — Chromium installed
  infra-tier: PRIMARY(real PostgreSQL) — isolated cluster, separate E2E and integration databases
  browser-tier: PRIMARY(Playwright)
startup: PASS — isolated backend API returned 401 unauthenticated; Vue and Flutter Web responded
integration: PASS — exit_code: 0, passed: 35, failed: 0, skipped: 0; PostgreSQL concurrency class: 3 passed
e2e: PASS — exit_code: 0, passed: 6, failed: 0; fund CRUD, validation, concurrency, corrections, reporting, audit, export/print, authorization, and regression flows
overall: PASS — tested Department Funds and Expenses workflows matched the disposable PostgreSQL records
```

## Track 2 Phase 4A — Attendance and QR

**Generated:** 2026-11-03
**Target:** `D:\Track2-Department-System`
**Scope:** Attendance and QR only. No Reports module work was performed.

### Existing Implementation

| Area | Existing behavior |
|---|---|
| Spring API | `GET/POST /api/attendance/events` lists and creates activities; `POST /api/attendance/scan` and `/api/attendance/records` record check-ins; `GET /api/attendance/records` lists records; `PUT /api/attendance/records/{id}` updates status; `/api/students/me/attendance` returns the authenticated student's own history. |
| Persistence and duplicate handling | `Attendance` stores student, event, status, and `scannedAt`; a database unique constraint covers `(student_id, event_id)`. The recording service rejects inactive/missing events and missing students, pre-checks duplicates, and maps a database uniqueness collision to HTTP 409. `scannedAt` is assigned by the server when creating the entity. |
| Vue | Admin and Officer pages create activities, select an event, enter a student ID or use the camera scanner, record attendance, update status, and display check-in records and timestamps. QR scanning decodes text into the same student-ID field used by manual recording. |
| Student QR | Flutter displays a QR code containing the student's ID only; it does not include credentials. The server validates the ID and event when the staff member submits the scan. |
| Student history | Spring returns each linked student's own event name/date, check-in timestamp, and status. Flutter already parsed the timestamp but did not show it in the attendance list. |
| Authorization and errors | Spring Security permits attendance routes to Admin and Officer, denies Treasurer and Student access to staff routes, and restricts Student portal routes to the student role. The API returns explicit not-found, inactive-event, and duplicate-check-in errors; Vue maps these to user-facing messages. |

### Finding and Change

1. **Flutter omitted the check-in time from attendance history.** The client parsed `scannedAt` but showed only activity date and status. Flutter history now displays server check-in time, and widget tests verify attendance time/status plus the student's QR display.
2. **The API's first scan response could disagree with the database timestamp precision.** Reproduced through the Vue/PostgreSQL workflow: `LocalDateTime.now()` carries nanoseconds, while PostgreSQL stores timestamps to microsecond precision, so immediate response JSON could have a trailing fractional digit not present after reread. Attendance timestamps are now truncated to microseconds at creation. PostgreSQL integration tests assert the returned value equals the persisted value.
3. Duplicate scan races were exercised both through concurrent PostgreSQL-backed service calls and simultaneous HTTP requests. The database unique constraint and conflict response produced exactly one saved row and HTTP 409 for competing duplicates; no defect was reproduced.

### Verification

| Check | Result | Exact evidence |
|---|---|---|
| Isolated PostgreSQL setup | PASS | PostgreSQL 18 tools at `C:\Program Files\PostgreSQL\18\bin`; fresh cluster on port 55436 with separate `track2_attendance_e2e` and `track2_attendance_maven` databases. Verified database name and server port before application startup or test writes. |
| Backend and UI startup | PASS | Spring Boot on 8082 returned `/api/health` → 200; authenticated attendance list returned 200 and empty before E2E writes. Vue/Vite on 5175 and Flutter Web on 8083 returned the running application. |
| Full Maven backend suite | PASS | `mvn -o test` with PostgreSQL test URL — exit 0; 37 tests, 0 failures, 0 errors, 0 skipped across 12 suites. |
| PostgreSQL attendance integration | PASS | Included in Maven suite — 2 tests passed. Valid scan, server timestamp equality after persistence, personal history, invalid student/event/inactive event, duplicate rejection, and concurrent duplicate writes. |
| Vue unit tests | PASS | `npm test` — exit 0; 2 passed, 0 failed. |
| Vue production build | PASS | `npm run build` — exit 0. |
| Playwright E2E suite | PASS | `npm run test:all` — exit 0; 2 frontend unit tests and 8 browser E2E tests passed (2 attendance, 6 existing finance/fund regression tests). |
| Flutter static analysis | PASS | `flutter analyze --no-pub` — exit 0; no issues. |
| Flutter tests | PASS | `flutter test --no-pub` — exit 0; 5 passed, 0 failed (3 API client and 2 widget tests). |
| Database persistence | PASS | Attendance E2E database read-back at end of browser run: 9 attendance rows across 9 fictional students/events. Browser reload retained the scan; API and Flutter student history matched the same event, date, timestamp, and status. |
| QR payload validation | PASS (automated payload) | Flutter widget test verifies the QR is displayed for the signed-in student's ID; Vue/API flow submits that student-number payload to `/attendance/records` and the backend resolves the correct student/event. This exercises the payload contract, not live camera decoding. |
| Physical camera QR scan | NOT TESTED | No physical device or camera-enabled emulator scan was performed. |
| Development data / credentials / GitHub | PRESERVED | Port 55432 was checked as a separate listener and never used by app/tests. Only fictional accounts, students, events, and attendance were written to disposable test databases. No commit or push was made. |

### PASS / FAIL / NOT TESTED Checklist

| Requirement | Status | Notes |
|---|---|---|
| Create events and show event details | PASS | Admin created an event via Vue; event title/date read back from PostgreSQL-backed API and was shown in attendance history. |
| Generate/display student QR | PASS (automated UI) | Flutter widget test verifies the QR widget and matching student ID are displayed; no credentials are encoded. |
| Validate QR payload and record correct student/event | PASS (automated payload) | Fictional student number submitted as the decoded QR text; response referenced the exact student and event. Camera decoding is separate and untested. |
| Duplicate and concurrent scan handling | PASS | Existing duplicate received 409; six concurrent HTTP requests produced one 201 and five 409; PostgreSQL service concurrency test persisted one row and returned one conflict. |
| Invalid student/event and inactive event | PASS | Missing student/event returned 404; inactive event returned 400; no extra attendance rows persisted. |
| Server-generated timestamp and database persistence | PASS | Timestamp generated server-side, within request bounds, truncated to PostgreSQL microsecond precision, and equal on database read-back. |
| Vue history, status, and browser refresh | PASS | Vue showed correct student/event/time/status; same record remained after browser reload and login. |
| Flutter student attendance history | PASS | Flutter rendered the same event/status and displayed its check-in time; the authenticated student API returned the exact Vue/PostgreSQL values. |
| Admin and Officer permissions | PASS | Both read attendance; Officer successfully created the test event and scanned. Admin created the Vue event and recorded attendance. |
| Treasurer and Student modification denial | PASS | Event creation and scans returned 403 for Treasurer and Student; both roles were denied staff event-list access. |
| Physical camera scanning | NOT TESTED | Requires a physical camera or camera-enabled emulator. |
| Reports module | NOT TESTED / OUT OF SCOPE | No Reports module work was performed. |

### Verdict

```text
environment:
  docker: UNAVAILABLE — Docker command not installed; independent local PostgreSQL 18 used
  node: AVAILABLE — v24.18.0
  playwright: AVAILABLE — Playwright 1.64.1; Chromium installed
  infra-tier: PRIMARY(real PostgreSQL) — disposable PostgreSQL 18 cluster on port 55436; separate app and Maven databases
  browser-tier: PRIMARY(Playwright) — headless Chromium; Vue and Flutter Web
startup: PASS — isolated Spring Boot health 200 on 8082; Vue on 5175 and Flutter Web on 8083 served successfully
integration: PASS — exit_code: 0, passed: 37, failed: 0, skipped: 0; PostgreSQL attendance: 2 passed, financial PostgreSQL concurrency: 3 passed
e2e: PASS — exit_code: 0, passed: 8, failed: 0; attendance event/scan/duplicate/concurrency/invalid references/permissions/history/refresh plus Flutter history; six existing financial/fund regressions
overall: PASS for automated Phase 4A attendance and QR payload flows — real camera scanning is NOT TESTED
```

### Remaining Work

- Test camera scanning on a physical Android device or camera-enabled emulator; automated QR payload/API coverage does not certify camera capture.
- No Reports module work was started.

## Phase 4B — Reports and Dashboard Stabilization

**Validation date:** 2026-11-03

**Scope:** Continue from the existing Phase 4A work; no restart or Phase 5 work was performed.

### Results

| Area | Status | Evidence |
|---|---|---|
| Dashboard and report calculations | PASS | Playwright reconciled the dashboard and report data with fictional PostgreSQL-backed student, finance, fund, and attendance records. |
| Financial totals and corrections | PASS | The end-to-end report scenario verified a PHP 100 fine less PHP 40 payment = PHP 60 unpaid; department income of PHP 1,000 less PHP 250 and PHP 150 expenses = PHP 600. Correction/reversal behavior was also exercised. |
| Attendance report consistency | PASS | PostgreSQL integration and browser flows checked event/student associations, attendance totals/statuses, duplicate handling, and history consistency. |
| Authorization and error handling | PASS | Empty-state/API tests checked unauthenticated and role-restricted report access, unknown report behavior, and visible dashboard request failures. CORS regression coverage accepts configured Vue/Flutter origins and rejects an unlisted origin. |
| CSV and printing | PASS | Playwright inspected downloaded CSV content and exercised print invocation/layout. No physical printer was used. |
| Refresh and persistence | PASS | Browser journeys verified refreshed UI against the persisted database-backed records. |
| Vue unit tests | PASS | 2 passed, 0 failed, 0 skipped. |
| Vue production build | PASS | `npm run build` completed successfully. |
| Backend Maven suite | PASS | 39 passed, 0 failed, 0 skipped. PostgreSQL-backed classes included 2 attendance, 3 financial-concurrency, and 1 Reports/Dashboard integration tests. |
| Full Playwright E2E suite | PASS | 11 passed, 0 failed. Included empty states, attendance/QR, financial collections, concurrency and reversal, funds and corrections, reports/dashboard, exports, print, and role checks. |
| Flutter source tests | NOT RERUN | Phase 4B changed no Flutter source; the existing Phase 4A analyzer and 5-test Flutter run remain the available mobile source-level evidence. Flutter Web journeys were exercised by Playwright. |
| Physical camera/printer | NOT TESTED | No physical camera scan or physical print was performed. |

### Confirmed fixes

1. **CORS allowed only one configured origin.** The configuration now accepts an explicit comma-separated origin allow-list for the Vue and Flutter Web clients while preserving rejection of unlisted origins. Regression coverage was added to `AdminAuthorizationTest`.
2. **Finance/fund success notices could appear before refreshed values.** The notices now follow completion of the refresh, preventing a success message from preceding stale rows. The full browser suite passed after this change.
3. **Order-sensitive funds concurrency setup.** The E2E fixture now normalizes its starting balance rather than depending on prior tests' records.
4. **Report test synchronization.** The report journey waits for the fixture row before asserting, avoiding a race with asynchronous report requests.
5. **CSV and dashboard reliability.** The Vue changes retain visible errors from parallel dashboard requests, protect CSV values that could be interpreted as formulas, format exported monetary values consistently, and clarify the zero-activity chart state. Unit/build and browser checks passed.

### Test commands and evidence

- `mvn -o test` against the disposable PostgreSQL test configuration — exit code 0; 39 tests, 0 failures, 0 errors, 0 skipped.
- `npm run test:all` — exit code 0; 2 unit tests and 11 Playwright tests passed.
- `npm run build` — exit code 0.
- PostgreSQL integration tests were included in the Maven suite: attendance 2, financial concurrency 3, Reports/Dashboard 1; all passed.
- The full Playwright suite exercised the live test services and disposable PostgreSQL data. No test credentials are recorded here.
- Phase 4A Flutter analyzer/tests previously passed (5 tests); Flutter source was unchanged in Phase 4B.

### Cleanup and repository state

- Test services on `8082`, `8083`, and `5175` and the disposable PostgreSQL cluster on `55437` were stopped. The disposable cluster directory and generated `frontend/test-results` artifact were removed.
- After cleanup, port `55432` remained listening as the separate development database; no tests targeted it.
- No commit or push was made. Existing Phase 4A changes and other workspace modifications were preserved.

### Phase 4B PASS / FAIL / NOT TESTED

| Check | Status | Notes |
|---|---|---|
| Dashboard/report values and PostgreSQL reconciliation | PASS | Fictional test data; dashboard, reports, API, and DB assertions passed. |
| Partial/settled financial balances and fund arithmetic | PASS | PHP 60 unpaid and PHP 600 remaining fund balance scenarios passed. |
| Attendance totals, status, history, and duplicate handling | PASS | PostgreSQL integration and E2E flows passed. |
| Admin, Treasurer, and Officer permissions | PASS | Tested through the browser/API suite; unauthorized report access was rejected. |
| CSV contents and print invocation | PASS | Automated download/content and print checks passed; no physical printer certification. |
| Backend, Vue, and Playwright verification | PASS | Maven 39/39; Vue 2/2 and build; Playwright 11/11; all commands exited 0. |
| Flutter analyzer/tests for Phase 4B | NOT RERUN | No mobile source was changed in this phase; prior Phase 4A results remain documented above. |
| Physical camera or printer operation | NOT TESTED | Requires real hardware or an appropriate device/emulator. |
| Development database on port 55432 | PASS / PRESERVED | It remained listening and was not used for test writes. |

```text
environment:
  docker: UNAVAILABLE — local disposable PostgreSQL was used instead
  node: AVAILABLE
  playwright: AVAILABLE — Chromium headless
  infra-tier: PRIMARY(real PostgreSQL) — disposable cluster on 127.0.0.1:55437
  browser-tier: PRIMARY(Playwright)
startup: PASS — isolated backend, Vue, and Flutter Web test services responded on 8082, 5175, and 8083
integration: PASS — exit_code: 0, passed: 39, failed: 0, skipped: 0; PostgreSQL integration subset: 6 passed
e2e: PASS — exit_code: 0, passed: 11, failed: 0; reports/dashboard, attendance/QR, finance, funds, roles, persistence, CSV, and print invocation
overall: PASS for automated Phase 4B Reports and Dashboard verification; physical camera/printer behavior was not tested
```

**Phase 4B complete. Phase 5 was not started.**

## Phase 5 — Flutter Student Portal Integration

**Validation date:** 2026-11-03

**Scope:** Existing Flutter Student Portal integrated and verified with Spring Boot and a disposable PostgreSQL database. Earlier Phase 4A/4B changes were preserved.

### Existing implementation reviewed

- Flutter files reviewed: `mobile/lib/api_client.dart`, `login_page.dart`, `portal_app.dart`, `student_home.dart`, and `student_models.dart`; existing API-client and widget tests were extended.
- The portal authenticates with the existing HTTP Basic Spring Security API, checks for `ROLE_STUDENT`, and loads only `/api/students/me/**` resources. The backend resolves the student linked to the authenticated username; it does not accept a student ID from the app to choose whose records to return.
- The interface already included a student dashboard/profile, outstanding balance, fines, payments, attendance history, refresh, bottom navigation, student-ID QR, error states, and logout. The app does not create payments or expose staff finance operations.
- Authentication credentials remain in memory and are cleared by logout. No real passwords, API keys, or credentials were added to source or this report.

### Confirmed fix

1. **Correction/reversal ledger records were omitted from the student’s history.** Both fine and payment lists filtered out entries marked as corrections, despite the backend exposing those entries and using their opposite ledger kind to adjust the balance. The portal now includes correction entries in the appropriate list, labels them “Balance correction”/“Correction,” and continues to show an original entry as “Corrected” when applicable. It does not expose the backend’s internal `REVERSAL:` marker to the student. A widget regression test covers correction entries in both lists.
2. **Malformed response values could appear as valid zero totals or silently disappear.** Numeric parsing now rejects invalid/non-finite amounts and invalid whole-number counts; list parsing rejects malformed rows instead of dropping them. The API client presents these as clear unexpected-response errors. API-client regression tests cover malformed balances and malformed list payloads.
3. **QR accessibility text did not identify whose QR was displayed.** The QR widget now has a screen-reader label that includes the linked student number. Widget coverage verifies the QR widget’s label and the visible student ID; existing QR source remains `QrImageView(data: studentNumber, ...)`.

### Isolated integration workflow

- Created a new PostgreSQL 18 cluster on loopback port `55438`, with separate app (`track2_phase5_mobile`) and Maven test (`track2_phase5_maven`) databases. The app database was verified as empty except for four fictional test-role accounts before E2E writes.
- Backend, Vue, and Flutter Web ran on ports `8082`, `5175`, and `8083`. Vue was explicitly configured with `VITE_API_URL=http://127.0.0.1:8082/api`; test credentials were supplied only to the isolated process environment.
- The E2E journey created a fictional student and linked student account as Admin, recorded PHP 100 fine as Admin and PHP 40 payment as Treasurer, and created an event/recorded a PRESENT check-in as Officer. Flutter then authenticated as the linked student and showed the profile, PHP 60 balance, fine/payment history, student ID QR, attendance event/date/time/status; logout, re-login, refresh, and invalid-login handling were checked.
- PostgreSQL read-back for the generated fictional student confirmed the linked account, FINE `100.00`, PAYMENT `40.00`, and one associated PRESENT attendance row. The authenticated student API returned the matching balance/history. Unauthenticated `/students/me/summary` returned 401, and student access to another student’s administrative record returned 403.
- This tests submission of the student-number QR payload through the existing attendance API flow and the matching Flutter QR widget value/label. It does **not** test optical camera decoding or a physical Vue camera scan.

### Verification

| Check | Result | Exact evidence |
|---|---|---|
| Flutter static analysis | PASS | `flutter analyze --no-pub` — exit 0, no issues. |
| Flutter tests | PASS | `flutter test --no-pub --reporter expanded` — 10 passed, 0 failed. Includes login error/role, unlinked account, malformed API responses, logout, profile QR label, attendance timestamp/status, and correction history. |
| Flutter Web production build | PASS | `flutter build web --no-pub` — exit 0; `build/web` generated. |
| Full backend suite | PASS | `mvn -o test` against the separate disposable PostgreSQL Maven database — exit 0; 39 passed, 0 failed, 0 errors, 0 skipped. The PostgreSQL integration subset included 2 attendance, 3 financial-concurrency, and 1 Reports/Dashboard tests. |
| Vue unit tests | PASS | `npm run test:all` — 2 passed, 0 failed. |
| Full Playwright E2E | PASS | Same command — 12 passed, 0 failed. Covered existing attendance/QR, finance, funds, reports/dashboard and role regressions, plus the new Flutter student portal end-to-end journey. |
| PostgreSQL student workflow reconciliation | PASS | SQL read-back and authenticated API/UI agreed: PHP 100 fine − PHP 40 payment = PHP 60 unpaid; the fictional student had one PRESENT record for the created event. |
| Android device/emulator | NOT TESTED | `flutter devices` found Windows, Chrome, and Edge only; `flutter emulators` reported no emulator sources. |
| Android APK | NOT TESTED | `flutter build apk --no-pub` exited 1: “No Android SDK found. Try setting the ANDROID_HOME environment variable.” `flutter doctor -v` also reports Android SDK unavailable. No SDK/toolchain was installed. |
| Physical camera scanning | NOT TESTED | No Android hardware or camera-enabled emulator was available; automated payload/API verification is not a physical scan. |
| Dart Tooling Daemon hot reload/restart | NOT VERIFIED | Both MCP attempts against the headless web debug app returned `Bad state: No element`. Flutter source analysis, tests, Web build, and the Flutter Web E2E did pass. |

### Test environment correction

The first full Playwright attempt against the clean database had 5 passed and 7 failed because Vite’s default `/api` path sent Vue login requests to the unrelated default backend at port 8080 rather than the isolated test backend. The failures were login timeouts; the setup and data APIs targeted only port 8082. Vite was restarted with the explicit isolated `VITE_API_URL`, the disposable app database was recreated empty, and the complete rerun passed all 12 Playwright tests. No database writes were directed to development port `55432`.

### Phase 5 PASS / FAIL / NOT TESTED

| Requirement | Status | Notes |
|---|---|---|
| Valid and invalid student login; student-only authorization | PASS | Invalid credentials show a clear error; non-student and unauthenticated access are rejected. |
| Correct account-to-student linkage and data isolation | PASS | Student’s `/me` profile matched the linked record; access to administrative student data returned 403. |
| Profile and real dashboard data | PASS | Profile, student number, course, year/section, outstanding balance, and attendance summary came from the authenticated backend. |
| Finance read-only history and currency values | PASS | Fine/payment history and PHP 60 balance matched API and PostgreSQL; corrections are now visible. |
| Student QR identifier | PASS (automated) | Widget QR source uses the linked student number; screen-reader label and visible ID are asserted. Actual optical decoding is untested. |
| Attendance event/date/check-in/status | PASS | Flutter history matched the event, timestamp, and status returned by the student API and persisted in PostgreSQL. |
| Refresh, logout, and re-login | PASS | Refresh retained current backend values; logout clears the client token and returns to sign-in; re-login loaded the same student data. |
| Flutter analysis, tests, Web build, and E2E | PASS | Analyzer clean, 10 Flutter tests, Web build, and all 12 Playwright E2E tests passed. |
| Android APK/device readiness | NOT TESTED | Android SDK and emulator are unavailable. |
| Physical QR camera scan | NOT TESTED | No suitable physical device or emulator was available. |

```text
environment:
  docker: UNAVAILABLE — local disposable PostgreSQL used
  node: AVAILABLE — v24.18.0
  playwright: AVAILABLE — 1.64.1 with installed Chromium
  infra-tier: PRIMARY(real PostgreSQL) — app and Maven databases on disposable loopback cluster port 55438
  browser-tier: PRIMARY(Playwright) — 12 browser E2E tests
startup: PASS — isolated Spring Boot/Vue/Flutter Web services responded on 8082/5175/8083
integration: PASS — exit_code: 0, passed: 39, failed: 0, skipped: 0; Flutter tests exit_code: 0, passed: 10, failed: 0
e2e: PASS — exit_code: 0, passed: 12, failed: 0; student onboarding, finance, attendance, profile, QR payload, refresh, logout, and regressions
overall: PASS for Flutter Student Portal integration and automated workflows; Android APK/device and physical camera scanning remain unverified
```

**Phase 5 complete as far as the available environment permits.**

## Phase 6 — Final Integration and Deployment Preparation

**Validation date:** 2026-11-03

**Scope:** Reviewed current configuration, Android readiness, deployment instructions, and secret-handling indicators. Preserved all Phase 4–5 work. No application source, database, service, or deployment target was changed.

### Android readiness

- The Android SDK was present locally at `tools/android-sdk` (not tracked), with Platform 36, Build-Tools 36.0.0, NDK 28.2.13676358, Command-line Tools `latest`, platform-tools, and accepted license files. The ignored `mobile/android/local.properties` points to that SDK.
- Flutter initially did not resolve the SDK from `local.properties`. Flutter's user-level configuration was set to the existing SDK and project JDK at `tools/jdk-21`; no SDK/JDK installation or download was performed. The process-level environment was not changed persistently.
- `flutter doctor -v` then reported the Android toolchain available: Android SDK 36.0.0, Platform 36, Build-Tools 36.0.0, JDK 21, and all Android licenses accepted. The overall doctor still reports missing Visual Studio, which is for Windows desktop builds and does not block Android.
- `flutter build apk --debug --no-pub` completed with exit code 0 and generated `mobile/build/app/outputs/flutter-apk/app-debug.apk`. Gradle emitted an SDK XML version warning but completed successfully.
- `flutter devices` listed Windows, Chrome, and Edge only. `flutter emulators` exited 1 with “Unable to find any emulator sources.” The APK was not installed or launched on a device/emulator. Physical camera QR scanning remains untested.
- Android `INTERNET` permission is present in the main manifest. Cleartext traffic is allowed only by the debug manifest. The release build type currently uses the debug signing configuration, so the generated APK is for local demonstration/testing and is not suitable for distribution.

### Configuration and security review

| Area | Finding |
|---|---|
| Database | Application default is PostgreSQL at `127.0.0.1:55432`; the workspace startup helper uses loopback. Development profile uses schema update; the default profile validates schema. PostgreSQL must remain unexposed. |
| API and CORS | API uses local port 8080 in the workspace setup. Default CORS is restricted to `http://localhost:5173`; LAN access and HTTPS are not configured. |
| Authentication and data scoping | HTTP Basic authentication is used. Existing role gates cover admin, finance, attendance, and student routes. Student self-service resolves records from the authenticated account; Phase 5 verified unauthorized/self-data access behavior. Basic credentials must not be sent over plain HTTP on shared networks. |
| Secrets | A high-confidence credential-pattern check of tracked non-documentation source found no likely committed secrets. The tracked `backend/.env.example` contains placeholders. Local ignored environment files were not read or reproduced in this report. |
| Production readiness | No production TLS endpoint, reverse proxy, firewall policy, production deployment configuration, or release signing setup is present. Do not expose the API or Swagger publicly without an approved security/deployment design. |

### Deployment instructions updated

`README.md` now documents the local backend/Vue/Flutter startup flow, emulator API address, Android SDK/JDK prerequisites and configuration, debug APK command/output, and the limitations for physical phones or other computers. It clarifies that a LAN demo requires an approved HTTPS endpoint, exact CORS origin, controlled binding/firewall rules, and must never expose PostgreSQL. Stale claims that APK generation was unverified and old test totals were corrected.

No public deployment was attempted. Another computer or physical Android phone cannot currently connect to the loopback-only backend; no LAN connectivity is claimed.

### Phase 6 verification

| Check | Result | Exact evidence |
|---|---|---|
| Android SDK/JDK discovery | PASS | Existing SDK components and JDK 21 verified at local paths; Flutter configured to use them. No SDK installation was required. |
| Flutter Android toolchain | PASS | `flutter doctor -v`: Android SDK 36.0.0, Build-Tools 36.0.0, JDK 21, and all licenses accepted. Overall doctor has an unrelated Visual Studio warning for Windows desktop development. |
| Android debug APK | PASS | `flutter build apk --debug --no-pub` — exit 0; `mobile/build/app/outputs/flutter-apk/app-debug.apk` generated. |
| Android emulator/device launch | NOT TESTED | No Android emulator sources or physical Android device were available. APK was not installed/launched. |
| Physical camera QR scan | NOT TESTED | No physical Android device/emulator available; automated QR payload/API tests from Phase 5 do not verify camera capture. |
| Release APK/Play Store signing | NOT TESTED | Current Gradle release configuration uses debug signing; no release keystore/configuration was created. |
| Existing automated regression evidence | PASS (prior run) | Phase 5: Maven 39/39 (including 6 PostgreSQL integration tests), Vue 2/2, Playwright 12/12, Flutter 10/10, Flutter analysis clean, Flutter Web build passed. |
| Full regression rerun in Phase 6 | NOT RUN | Phase 6 application changes were documentation-only; no application source/configuration changed. |
| Development DB and services | PASS (preserved) | No database writes or service restarts were performed. Port 55432 was not used for Phase 6 testing. |
| `git diff --check` | PASS | No whitespace errors; existing Java line-ending warnings remain informational. |

### Phase 6 PASS / FAIL / NOT TESTED

| Requirement | Status | Notes |
|---|---|---|
| Preserve Phases 4–5 and document deployment setup | PASS | Existing changes preserved; README and this report updated. |
| Android SDK/JDK readiness and debug APK build | PASS | Existing local toolchain configured; debug APK successfully generated without installing SDK components. |
| Public deployment / cross-device network connectivity | NOT TESTED | No deployment or LAN binding/firewall/CORS/TLS changes were made. Current backend remains loopback-only. |
| Android device installation and execution | NOT TESTED | No emulator or physical Android device available. |
| Physical QR camera scanning | NOT TESTED | Only the earlier automated payload/API flow was exercised. |
| Release signing and store readiness | FAIL / BLOCKED | Release currently reuses debug signing; a protected release keystore and signing configuration are still required. |

**Git state:** latest commit remains `c7c437a`. Existing uncommitted Phase 4–5 changes were preserved; Phase 6 changed only `README.md` and this report in the worktree. No commit or push was made.

**Overall:** Local demonstration preparation is improved and Android debug APK generation is verified. Public deployment, cross-device connectivity, Android device execution, camera scanning, and release signing remain unverified or blocked.
