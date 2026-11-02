import { expect, test } from '@playwright/test';

import {
  addFundEntry,
  authenticatedApi,
  navigate,
  selectFundType,
  signIn,
  signOut,
  testPassword,
} from './financial-helpers.js';

test('records exactly PHP 1000 income minus PHP 250 and PHP 150 expenses in Vue', async ({ page }) => {
  test.setTimeout(60_000);
  const adminPassword = testPassword('ADMIN');
  const treasurerPassword = testPassword('TREASURER');
  const adminApi = await authenticatedApi('admin', adminPassword);
  const treasurerApi = await authenticatedApi('treasurer', treasurerPassword);
  const responses = [];

  try {
    const baseline = Number((await (await adminApi.get('/api/dashboard')).json()).fundBalance);
    page.on('response', response => {
      if (new URL(response.url()).pathname === '/api/finance/funds') {
        responses.push({ method: response.request().method(), status: response.status() });
      }
    });

    await signIn(page, 'admin', adminPassword);
    await navigate(page, 'Department Funds');
    await selectFundType(page, 'Opening balance');
    await page.getByRole('spinbutton', { name: 'Amount (₱)' }).fill('1000');
    await page.getByRole('textbox', { name: 'Description or payee' }).fill('E2E opening fund');
    const openingRequest = page.waitForResponse(response =>
      response.url().endsWith('/api/finance/funds') && response.request().method() === 'POST');
    await page.getByRole('button', { name: 'Record fund entry' }).evaluate(button => {
      const form = button.closest('form');
      form.requestSubmit();
      form.requestSubmit();
    });
    expect((await openingRequest).status()).toBe(201);
    await expect(page.getByText('Fund entry recorded.', { exact: true })).toBeVisible();
    await expect(page.getByText('E2E opening fund', { exact: true })).toBeVisible();
    expect(responses.filter(response => response.method === 'POST')).toHaveLength(1);

    const openingRows = await (await adminApi.get('/api/finance/funds')).json();
    const opening = openingRows.find(row => row.description === 'E2E opening fund');
    expect(opening).toBeTruthy();
    expect(opening.type).toBe('OPENING_BALANCE');
    expect(Number(opening.amount)).toBe(1000);
    expect(Number((await (await adminApi.get('/api/dashboard')).json()).fundBalance)).toBe(baseline + 1000);

    await signOut(page);
    await signIn(page, 'treasurer', treasurerPassword);
    await navigate(page, 'Expenses');
    await page.getByRole('spinbutton', { name: 'Expense amount (₱)' }).fill('250');
    await page.getByRole('textbox', { name: 'Payee or reason' }).fill('E2E expense one');
    let responseWait = page.waitForResponse(response =>
      response.url().endsWith('/api/finance/funds') && response.request().method() === 'POST');
    await page.getByRole('button', { name: 'Record expense' }).click();
    expect((await responseWait).status()).toBe(201);
    await expect(page.getByText('Expense recorded.', { exact: true })).toBeVisible();

    await page.getByRole('spinbutton', { name: 'Expense amount (₱)' }).fill('150');
    await page.getByRole('textbox', { name: 'Payee or reason' }).fill('E2E expense two');
    responseWait = page.waitForResponse(response =>
      response.url().endsWith('/api/finance/funds') && response.request().method() === 'POST');
    await page.getByRole('button', { name: 'Record expense' }).click();
    expect((await responseWait).status()).toBe(201);
    await expect(page.getByText('E2E expense two', { exact: true })).toBeVisible();

    const fundRows = await (await treasurerApi.get('/api/finance/funds')).json();
    const expectedRows = fundRows.filter(row =>
      ['E2E opening fund', 'E2E expense one', 'E2E expense two'].includes(row.description));
    expect(expectedRows).toHaveLength(3);
    expect(expectedRows.reduce((balance, row) =>
      balance + (row.type === 'DISBURSEMENT' ? -Number(row.amount) : Number(row.amount)), 0)).toBe(600);
    expect(Number((await (await treasurerApi.get('/api/dashboard')).json()).fundBalance)).toBe(baseline + 600);

    await navigate(page, 'Dashboard');
    const fundCard = page.locator('.metric-card').filter({ hasText: 'Fund balance' });
    const displayedBalance = (baseline + 600).toLocaleString('en-PH', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    });
    await expect(fundCard).toContainText(`₱${displayedBalance}`);
    await page.reload();
    await signIn(page, 'treasurer', treasurerPassword);
    await navigate(page, 'Expenses');
    await expect(page.getByText('E2E expense one', { exact: true })).toBeVisible();
    await expect(page.getByText('E2E expense two', { exact: true })).toBeVisible();

    expect(responses.filter(response => response.method === 'POST').map(response => response.status))
      .toEqual([201, 201, 201]);
  } finally {
    await adminApi.dispose();
    await treasurerApi.dispose();
  }
});
