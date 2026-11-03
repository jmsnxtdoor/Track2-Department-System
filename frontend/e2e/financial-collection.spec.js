import { expect, test } from '@playwright/test';

import {
  apiBaseUrl,
  authenticatedApi,
  createStudent,
  navigate,
  selectStudent,
  signIn,
  testPassword,
} from './financial-helpers.js';

test('admin fine and treasurer collection stay consistent with portal, dashboard, and reports', async ({ page, request }) => {
  test.setTimeout(90_000);
  const adminPassword = testPassword('ADMIN');
  const treasurerPassword = testPassword('TREASURER');
  const adminApi = await authenticatedApi('admin', adminPassword);
  const treasurerApi = await authenticatedApi('treasurer', treasurerPassword);
  let studentApi;
  const uiResponses = [];

  try {
    expect((await request.get(`${apiBaseUrl}/finance/ledger`)).status()).toBe(401);
    const baselineDashboard = await (await treasurerApi.get('/api/dashboard')).json();
    const baselineCollections = Number(baselineDashboard.collections);
    const baselineUnpaid = Number(baselineDashboard.unpaid);
    const student = await createStudent(adminApi);
    const accountName = `e2e-${student.studentNumber.toLowerCase()}`;
    const createAccount = await adminApi.post('/api/admin/users', {
      data: { username: accountName, role: 'STUDENT', password: testPassword('STUDENT') },
    });
    expect(createAccount.status()).toBe(201);
    const account = await createAccount.json();
    expect((await adminApi.put(`/api/admin/users/${account.id}/student-link`, {
      data: { studentId: student.id },
    })).status()).toBe(200);
    studentApi = await authenticatedApi(accountName, testPassword('STUDENT'));

    page.on('response', response => {
      const url = new URL(response.url());
      if (url.pathname.startsWith('/api/')) {
        uiResponses.push({
          method: response.request().method(),
          endpoint: url.pathname,
          status: response.status(),
        });
      }
    });
    await signIn(page, 'admin', adminPassword);
    await navigate(page, 'Student Fines');
    await selectStudent(page, student);
    await page.getByRole('spinbutton', { name: 'Amount (₱)' }).fill('100');
    await page.getByRole('textbox', { name: 'Reason for fine' }).fill('E2E annual activity');
    let fineRequestCount = 0;
    page.on('request', outgoingRequest => {
      if (outgoingRequest.url().endsWith('/api/finance/ledger') && outgoingRequest.method() === 'POST') {
        fineRequestCount++;
      }
    });
    const fineResponse = page.waitForResponse(response =>
      response.url().endsWith('/api/finance/ledger') && response.request().method() === 'POST');
    await page.getByRole('button', { name: 'Record fine' }).evaluate(button => {
      const form = button.closest('form');
      form.requestSubmit();
      form.requestSubmit();
    });
    expect((await fineResponse).status()).toBe(201);
    await page.waitForTimeout(100);
    expect(fineRequestCount).toBe(1);
    await expect(page.getByText('Fine recorded.', { exact: true })).toBeVisible();
    await expect(page.getByRole('row').filter({ hasText: student.fullName })).toContainText('₱100.00');

    await page.getByRole('button', { name: 'Open profile menu' }).click();
    await page.getByText('Log out', { exact: true }).click();
    await signIn(page, 'treasurer', treasurerPassword);
    await navigate(page, 'Payments');
    await selectStudent(page, student);
    await page.getByRole('spinbutton', { name: 'Amount (₱)' }).fill('40');
    await page.getByRole('textbox', { name: 'Payment description' }).fill('E2E partial collection');
    const paymentResponse = page.waitForResponse(response =>
      response.url().endsWith('/api/finance/ledger') && response.request().method() === 'POST');
    await page.getByRole('button', { name: 'Record payment' }).click();
    expect((await paymentResponse).status()).toBe(201);
    await expect(page.getByText('Payment recorded.', { exact: true })).toBeVisible();

    const ledgerResponse = await treasurerApi.get('/api/finance/ledger');
    expect(ledgerResponse.status()).toBe(200);
    const ledger = await ledgerResponse.json();
    const studentLedger = ledger.filter(entry => entry.student.id === student.id);
    expect(studentLedger.map(entry => [entry.kind, Number(entry.amount)]))
      .toEqual(expect.arrayContaining([['FINE', 100], ['PAYMENT', 40]]));

    const dashboard = await (await treasurerApi.get('/api/dashboard')).json();
    expect(Number(dashboard.collections)).toBe(baselineCollections + 40);
    expect(Number(dashboard.unpaid)).toBe(baselineUnpaid + 60);
    const financeReport = await (await treasurerApi.get('/api/reports/finance')).json();
    expect(Number(financeReport.totalCollections)).toBe(baselineCollections + 40);
    expect(Number(financeReport.balances.find(row => row.studentNumber === student.studentNumber).unpaidBalance)).toBe(60);

    const summary = await studentApi.get('/api/students/me/summary');
    expect(summary.status()).toBe(200);
    const studentSummary = await summary.json();
    expect(studentSummary.student.studentNumber).toBe(student.studentNumber);
    expect(Number(studentSummary.unpaidBalance)).toBe(60);
    const history = await studentApi.get('/api/students/me/ledger');
    expect(history.status()).toBe(200);
    expect((await history.json()).map(entry => [entry.kind, Number(entry.amount)]))
      .toEqual(expect.arrayContaining([['FINE', 100], ['PAYMENT', 40]]));

    await navigate(page, 'Dashboard');
    const collectionCard = page.locator('.metric-card').filter({ hasText: 'Total payments collected' });
    await expect(collectionCard).toContainText(`₱${(baselineCollections + 40).toFixed(2)}`);
    await navigate(page, 'Unpaid Balances');
    await expect(page.getByRole('row').filter({ hasText: student.studentNumber })).toContainText('₱60.00');
    await navigate(page, 'Reports');
    await page.getByRole('combobox', { name: 'Report' }).press('ArrowDown');
    await page.getByRole('option', { name: 'Student fines and payments' }).click();
    await expect(page.getByRole('row').filter({ hasText: student.studentNumber })).toHaveCount(2);
    await page.getByRole('button', { name: 'Refresh' }).click();
    await page.reload();
    await signIn(page, 'treasurer', treasurerPassword);
    await navigate(page, 'Payments');
    await expect(page.getByRole('row').filter({ hasText: student.fullName })).toContainText('₱40.00');
    expect(uiResponses.filter(response => response.status >= 400)).toEqual([]);
    expect(uiResponses.filter(response =>
      response.endpoint === '/api/finance/ledger' && response.method === 'POST'
    ).map(response => response.status)).toEqual([201, 201]);

    const flutterPage = await page.context().newPage();
    try {
      await flutterPage.goto('http://127.0.0.1:8083', { waitUntil: 'domcontentloaded' });
      await flutterPage.locator('flt-semantics-placeholder').evaluate(element => element.click());
      const apiUrl = flutterPage.getByRole('textbox', { name: 'API server URL' });
      await apiUrl.click({ force: true });
      await apiUrl.press('Control+A');
      await apiUrl.pressSequentially('http://127.0.0.1:8082/api');
      const flutterUsername = flutterPage.getByRole('textbox', { name: 'Username' });
      await flutterUsername.click({ force: true });
      await flutterUsername.pressSequentially(accountName);
      const flutterPassword = flutterPage.getByRole('textbox', { name: 'Password' });
      await flutterPassword.click({ force: true });
      await flutterPassword.pressSequentially(testPassword('STUDENT'));
      await flutterPage.getByRole('button', { name: 'Sign in' }).click();
      await expect(flutterPage.getByRole('group', { name: new RegExp(student.fullName) })).toBeVisible();
      await expect(flutterPage.getByText('₱60.00').first()).toBeVisible();
      await flutterPage.getByRole('tab', { name: 'Payments' }).click();
      await expect(flutterPage.getByText('E2E partial collection')).toBeVisible();
      await expect(flutterPage.getByText('₱40.00').first()).toBeVisible();
      await flutterPage.getByRole('tab', { name: 'Fines' }).click();
      await expect(flutterPage.getByText('E2E annual activity')).toBeVisible();
      await expect(flutterPage.getByText('₱100.00').first()).toBeVisible();
    } finally {
      await flutterPage.close();
    }
  } finally {
    await adminApi.dispose();
    await treasurerApi.dispose();
    if (studentApi) await studentApi.dispose();
  }
});
