import { readFile } from 'node:fs/promises';
import { expect, test } from '@playwright/test';

import {
  addFundEntry,
  authenticatedApi,
  navigate,
  signIn,
  testPassword,
} from './financial-helpers.js';

test('corrects fund entries and verifies report export, printing, and audit records', async ({ page }) => {
  test.setTimeout(60_000);
  const adminPassword = testPassword('ADMIN');
  const adminApi = await authenticatedApi('admin', adminPassword);
  try {
    const baseline = Number((await (await adminApi.get('/api/dashboard')).json()).fundBalance);
    const addition = await addFundEntry(adminApi, 'ADDITION', 100, 'E2E correction income');
    expect(addition.status()).toBe(201);
    const expense = await addFundEntry(adminApi, 'DISBURSEMENT', 30, 'E2E correction expense');
    expect(expense.status()).toBe(201);
    const additionEntry = await addition.json();
    const expenseEntry = await expense.json();

    await signIn(page, 'admin', adminPassword);
    await navigate(page, 'Expenses');
    const expenseRow = page.getByRole('row').filter({ hasText: 'E2E correction expense' });
    await expenseRow.getByRole('button', { name: 'Correct entry' }).click();
    await page.getByRole('textbox', { name: 'Reason for correction' }).fill('Correct the E2E expense');
    const correctionResponse = page.waitForResponse(response =>
      response.url().endsWith(`/api/finance/funds/${expenseEntry.id}/reverse`));
    await page.getByRole('button', { name: 'Add correction' }).click();
    expect((await correctionResponse).status()).toBe(201);
    await expect(page.getByText('A correction entry was added. The original transaction remains recorded.')).toBeVisible();
    await expect(expenseRow).toContainText('Correction recorded');

    const reverseAddition = await adminApi.post(`/api/finance/funds/${additionEntry.id}/reverse`, {
      data: { reason: 'Correct the E2E income' },
    });
    expect(reverseAddition.status()).toBe(201);
    const duplicateReversal = await adminApi.post(`/api/finance/funds/${additionEntry.id}/reverse`, {
      data: { reason: 'Duplicate correction' },
    });
    expect(duplicateReversal.status()).toBe(409);
    expect((await duplicateReversal.json()).detail).toBe('This fund entry has already been reversed.');
    const missingReversal = await adminApi.post('/api/finance/funds/999999999/reverse', {
      data: { reason: 'Unknown record' },
    });
    expect(missingReversal.status()).toBe(404);
    expect((await missingReversal.json()).detail).toBe('Fund entry not found.');

    const funds = await (await adminApi.get('/api/reports/funds')).json();
    const corrections = funds.filter(row =>
      row.description.startsWith(`REVERSAL:DISBURSEMENT:${expenseEntry.id}:`)
      || row.description.startsWith(`REVERSAL:ADDITION:${additionEntry.id}:`));
    expect(corrections).toHaveLength(2);
    const balance = funds.reduce((total, row) =>
      total + (row.type === 'DISBURSEMENT' ? -Number(row.amount) : Number(row.amount)), 0);
    expect(balance).toBe(baseline);
    expect(Number((await (await adminApi.get('/api/dashboard')).json()).fundBalance)).toBe(baseline);

    const auditLogs = await (await adminApi.get('/api/admin/audit-logs')).json();
    expect(auditLogs.some(log =>
      log.action === 'FUND_ENTRY_CREATED' && log.entityId === additionEntry.id.toString())).toBe(true);
    expect(auditLogs.some(log =>
      log.action === 'FUND_ENTRY_CREATED' && log.entityId === expenseEntry.id.toString())).toBe(true);
    expect(auditLogs.some(log =>
      log.action === 'FUND_ENTRY_REVERSED' && log.entityId === expenseEntry.id.toString())).toBe(true);

    await navigate(page, 'Reports');
    await page.getByRole('combobox', { name: 'Report' }).press('ArrowDown');
    await page.getByRole('option', { name: 'Department funds and expenses' }).click();
    await expect(page.getByRole('row').filter({ hasText: 'E2E correction expense' })).toBeVisible();
    const downloadWait = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Download CSV for Excel' }).click();
    const download = await downloadWait;
    expect(download.suggestedFilename()).toBe('funds-disbursements.csv');
    const csv = await readFile(await download.path(), 'utf8');
    expect(csv).toContain('"createdAt","type","amount","description"');
    expect(csv).toContain('E2E correction expense');
    expect(csv).toContain('E2E correction income');

    await page.evaluate(() => {
      window.print = () => { window.__fundReportPrintCalled = true; };
    });
    await page.getByRole('button', { name: 'Print or save as PDF' }).click();
    expect(await page.evaluate(() => window.__fundReportPrintCalled)).toBe(true);
  } finally {
    await adminApi.dispose();
  }
});
