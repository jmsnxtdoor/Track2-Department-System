import { expect, test } from '@playwright/test';

import {
  addFundEntry,
  authenticatedApi,
  testPassword,
} from './financial-helpers.js';

test('protects fund mutations and serializes concurrent expenses', async ({ request }) => {
  const adminApi = await authenticatedApi('admin', testPassword('ADMIN'));
  const treasurerApi = await authenticatedApi('treasurer', testPassword('TREASURER'));
  const officerApi = await authenticatedApi('officer', testPassword('OFFICER'));
  const studentApi = await authenticatedApi('student', testPassword('STUDENT'));
  try {
    expect((await request.get('/api/finance/funds')).status()).toBe(401);
    expect((await officerApi.post('/api/finance/funds', {
      data: { type: 'ADDITION', amount: 10, description: 'Unauthorized officer' },
    })).status()).toBe(403);
    expect((await studentApi.post('/api/finance/funds', {
      data: { type: 'DISBURSEMENT', amount: 10, description: 'Unauthorized student' },
    })).status()).toBe(403);

    const baseline = Number((await (await adminApi.get('/api/dashboard')).json()).fundBalance);
    const openingAmount = 1000 - baseline;
    expect(openingAmount).toBeGreaterThan(0);
    const opening = await addFundEntry(adminApi, 'OPENING_BALANCE', openingAmount, 'E2E concurrency opening');
    expect(opening.status()).toBe(201);
    const malformed = await treasurerApi.post('/api/finance/funds', {
      data: { type: 'ADDITION', amount: 10 },
    });
    expect(malformed.status()).toBe(400);

    for (const [amount, description] of [[0, 'Zero fund amount'], [-1, 'Negative fund amount']]) {
      const response = await addFundEntry(treasurerApi, 'ADDITION', amount, description);
      expect(response.status()).toBe(400);
      expect((await response.json()).detail).toBeTruthy();
    }

    const expenses = await Promise.all(Array.from({ length: 4 }, (_, index) =>
      addFundEntry(treasurerApi, 'DISBURSEMENT', 600, `E2E concurrent expense ${index + 1}`)));
    expect(expenses.filter(response => response.status() === 201)).toHaveLength(1);
    expect(expenses.filter(response => response.status() === 400)).toHaveLength(3);
    for (const response of expenses.filter(result => result.status() === 400)) {
      expect((await response.json()).detail).toBe('Insufficient fund balance.');
    }

    const tooMuch = await addFundEntry(treasurerApi, 'DISBURSEMENT', 401, 'E2E excess expense');
    expect(tooMuch.status()).toBe(400);
    expect((await tooMuch.json()).detail).toBe('Insufficient fund balance.');

    const entries = await (await treasurerApi.get('/api/finance/funds')).json();
    const scenarioEntries = entries.filter(row =>
      row.description === 'E2E concurrency opening'
      || row.description.startsWith('E2E concurrent expense '));
    expect(scenarioEntries).toHaveLength(2);
    expect(scenarioEntries.reduce((total, row) =>
      total + (row.type === 'DISBURSEMENT' ? -Number(row.amount) : Number(row.amount)), 0))
      .toBe(openingAmount - 600);
    expect(Number((await (await treasurerApi.get('/api/dashboard')).json()).fundBalance)).toBe(400);
  } finally {
    await adminApi.dispose();
    await treasurerApi.dispose();
    await officerApi.dispose();
    await studentApi.dispose();
  }
});
