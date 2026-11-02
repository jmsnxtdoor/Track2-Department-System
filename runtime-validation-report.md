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
