import { expect, test } from '@playwright/test';

import {
  addLedgerEntry,
  addFundEntry,
  authenticatedApi,
  createStudent,
  newIdempotencyKey,
  testPassword,
} from './financial-helpers.js';

test('replays ledger and fund retries once and accepts separate equal-value requests', async () => {
  const adminApi = await authenticatedApi('admin', testPassword('ADMIN'));
  const treasurerApi = await authenticatedApi('treasurer', testPassword('TREASURER'));
  try {
    const baselineDashboard = await (await treasurerApi.get('/api/dashboard')).json();
    const student = await createStudent(adminApi, 'Idempotency');
    const fineKey = newIdempotencyKey();
    const fineResponses = await Promise.all([
      treasurerApi.post('/api/finance/ledger', {
        data: {
          studentId: student.id,
          kind: 'FINE',
          amount: 100,
          description: 'Idempotent test fine',
          idempotencyKey: fineKey,
        },
      }),
      treasurerApi.post('/api/finance/ledger', {
        data: {
          studentId: student.id,
          kind: 'FINE',
          amount: 100,
          description: 'Idempotent test fine',
          idempotencyKey: fineKey,
        },
      }),
    ]);
    expect(fineResponses.map(response => response.status())).toEqual([201, 201]);
    const fineRows = await Promise.all(fineResponses.map(response => response.json()));
    expect(fineRows[0].id).toBe(fineRows[1].id);
    expect((await (await adminApi.get('/api/finance/ledger')).json())
      .filter(row => row.student.id === student.id && row.description === 'Idempotent test fine')).toHaveLength(1);

    const paymentKey = newIdempotencyKey();
    const paymentPayload = {
      studentId: student.id,
      kind: 'PAYMENT',
      amount: 40,
      description: 'Idempotent test payment',
      idempotencyKey: paymentKey,
    };
    const payment = await treasurerApi.post('/api/finance/ledger', { data: paymentPayload });
    const paymentRetry = await treasurerApi.post('/api/finance/ledger', { data: paymentPayload });
    expect(payment.status()).toBe(201);
    expect((await paymentRetry.json()).id).toBe((await payment.json()).id);

    const changedDetails = await treasurerApi.post('/api/finance/ledger', {
      data: { ...paymentPayload, amount: 41 },
    });
    expect(changedDetails.status()).toBe(409);
    expect((await changedDetails.json()).detail)
      .toBe('Idempotency key was already used with different transaction details.');

    const separatePayment = await addLedgerEntry(
      treasurerApi, student.id, 'PAYMENT', 40, 'Separate equal-value payment', null,
    );
    expect(separatePayment.status()).toBe(201);
    expect(Number((await (await adminApi.get(`/api/students/${student.id}/balance`)).json()).balance)).toBe(20);

    const fundKey = newIdempotencyKey();
    const fundPayload = {
      type: 'ADDITION',
      amount: 50,
      description: 'Idempotent test addition',
      idempotencyKey: fundKey,
    };
    const fundResponses = await Promise.all([
      treasurerApi.post('/api/finance/funds', { data: fundPayload }),
      treasurerApi.post('/api/finance/funds', { data: fundPayload }),
    ]);
    expect(fundResponses.map(response => response.status())).toEqual([201, 201]);
    const fundRows = await Promise.all(fundResponses.map(response => response.json()));
    expect(fundRows[0].id).toBe(fundRows[1].id);

    const separateFund = await addFundEntry(
      treasurerApi, 'ADDITION', 50, 'Separate equal-value addition',
    );
    expect(separateFund.status()).toBe(201);

    const dashboard = await (await treasurerApi.get('/api/dashboard')).json();
    expect(Number(dashboard.fundBalance)).toBe(Number(baselineDashboard.fundBalance) + 100);
    expect(Number(dashboard.collections)).toBe(Number(baselineDashboard.collections) + 80);
  } finally {
    await adminApi.dispose();
    await treasurerApi.dispose();
  }
});
