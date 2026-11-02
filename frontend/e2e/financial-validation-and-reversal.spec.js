import { expect, test } from '@playwright/test';

import {
  addLedgerEntry,
  authenticatedApi,
  createStudent,
  testPassword,
} from './financial-helpers.js';

test('rejects invalid and excess payments and offsets corrections in reports and balance', async ({ request }) => {
  const adminApi = await authenticatedApi('admin', testPassword('ADMIN'));
  const treasurerApi = await authenticatedApi('treasurer', testPassword('TREASURER'));
  try {
    expect((await request.get('/api/reports/finance')).status()).toBe(401);
    const baselineReport = await (await treasurerApi.get('/api/reports/finance')).json();
    const baselineCollections = Number(baselineReport.totalCollections);
    const student = await createStudent(adminApi, 'Integrity');
    const fine = await addLedgerEntry(adminApi, student.id, 'FINE', 100, 'Original test fine', `FINE-${student.id}`);
    expect(fine.status()).toBe(201);
    const fineId = (await fine.json()).id;

    for (const [amount, referenceNumber] of [[0, `ZERO-${student.id}`], [-1, `NEG-${student.id}`]]) {
      const invalid = await addLedgerEntry(treasurerApi, student.id, 'PAYMENT', amount, 'Invalid amount', referenceNumber);
      expect(new URL(invalid.url()).pathname).toBe('/api/finance/ledger');
      expect(invalid.status()).toBe(400);
      expect((await invalid.json()).detail).toBeTruthy();
    }

    const missingStudent = await addLedgerEntry(
      treasurerApi, 999999999, 'PAYMENT', 1, 'Missing student', `MISS-${student.id}`,
    );
    expect(missingStudent.status()).toBe(404);
    expect((await missingStudent.json()).detail).toBe('Student not found.');

    const overpayment = await addLedgerEntry(
      treasurerApi, student.id, 'PAYMENT', 101, 'Overpayment', `OVER-${student.id}`,
    );
    expect(overpayment.status()).toBe(400);
    expect((await overpayment.json()).detail).toBe('Payment exceeds unpaid balance.');

    const duplicateReference = await addLedgerEntry(
      treasurerApi, student.id, 'FINE', 1, 'Duplicate reference', `FINE-${student.id}`,
    );
    expect(duplicateReference.status()).toBe(409);
    expect((await duplicateReference.json()).detail).toBe('Transaction reference already exists.');

    const partial = await addLedgerEntry(treasurerApi, student.id, 'PAYMENT', 40, 'Partial payment', `PART-${student.id}`);
    expect(partial.status()).toBe(201);
    const full = await addLedgerEntry(treasurerApi, student.id, 'PAYMENT', 60, 'Balance payment', `FULL-${student.id}`);
    expect(full.status()).toBe(201);
    const zeroBalancePayment = await addLedgerEntry(
      treasurerApi, student.id, 'PAYMENT', 0.01, 'No balance', `NONE-${student.id}`,
    );
    expect(zeroBalancePayment.status()).toBe(400);
    expect((await zeroBalancePayment.json()).detail).toBe('Payment exceeds unpaid balance.');

    const fullEntry = await full.json();
    const reversalResponse = await treasurerApi.post(`/api/finance/ledger/${fullEntry.id}/reverse`, {
      data: { reason: 'Correct test payment' },
    });
    expect(reversalResponse.status()).toBe(201);
    const afterPaymentCorrection = await treasurerApi.get(`/api/students/${student.id}/balance`);
    expect(afterPaymentCorrection.status()).toBe(200);
    expect(Number((await afterPaymentCorrection.json()).balance)).toBe(60);

    const partialEntry = await partial.json();
    expect((await treasurerApi.post(`/api/finance/ledger/${partialEntry.id}/reverse`, {
      data: { reason: 'Correct partial test payment' },
    })).status()).toBe(201);
    expect((await treasurerApi.post(`/api/finance/ledger/${fineId}/reverse`, {
      data: { reason: 'Remove original test fine' },
    })).status()).toBe(201);

    const ledger = await (await treasurerApi.get('/api/finance/ledger')).json();
    const balance = ledger
      .filter(entry => entry.student.id === student.id)
      .reduce((sum, entry) => sum + (entry.kind === 'FINE' ? Number(entry.amount) : -Number(entry.amount)), 0);
    expect(balance).toBe(0);
    const dashboard = await (await treasurerApi.get('/api/dashboard')).json();
    expect(Number(dashboard.collections)).toBe(baselineCollections);
    const report = await (await treasurerApi.get('/api/reports/finance')).json();
    expect(Number(report.totalCollections)).toBe(baselineCollections);
    expect(Number(report.balances.find(row => row.studentNumber === student.studentNumber).unpaidBalance)).toBe(0);
  } finally {
    await adminApi.dispose();
    await treasurerApi.dispose();
  }
});
