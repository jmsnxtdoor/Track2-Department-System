import { expect, test } from '@playwright/test';

import {
  addLedgerEntry,
  authenticatedApi,
  createStudent,
  testPassword,
} from './financial-helpers.js';

test('concurrent payments cannot overdraw a student and financial routes remain role protected', async ({ request }) => {
  const adminApi = await authenticatedApi('admin', testPassword('ADMIN'));
  const treasurerApi = await authenticatedApi('treasurer', testPassword('TREASURER'));
  const officerApi = await authenticatedApi('officer', testPassword('OFFICER'));
  const studentApi = await authenticatedApi('student', testPassword('STUDENT'));
  try {
    expect((await officerApi.post('/api/finance/ledger', {
      data: { studentId: 1, kind: 'FINE', amount: 10, description: 'Unauthorized' },
    })).status()).toBe(403);
    expect((await studentApi.post('/api/finance/ledger', {
      data: { studentId: 1, kind: 'PAYMENT', amount: 10, description: 'Unauthorized' },
    })).status()).toBe(403);

    const student = await createStudent(adminApi, 'Concurrent');
    expect((await addLedgerEntry(adminApi, student.id, 'FINE', 100, 'Concurrent balance', `CONC-F-${student.id}`)).status()).toBe(201);
    const linkedUser = await adminApi.post('/api/admin/users', {
      data: { username: `concurrent-${student.studentNumber.toLowerCase()}`, role: 'STUDENT', password: testPassword('STUDENT') },
    });
    expect(linkedUser.status()).toBe(201);
    const account = await linkedUser.json();
    expect((await adminApi.put(`/api/admin/users/${account.id}/student-link`, {
      data: { studentId: student.id },
    })).status()).toBe(200);
    const linkedStudentApi = await authenticatedApi(account.username, testPassword('STUDENT'));

    const responses = await Promise.all(Array.from({ length: 4 }, (_, index) =>
      addLedgerEntry(
        treasurerApi,
        student.id,
        'PAYMENT',
        60,
        `Concurrent payment ${index + 1}`,
        `CONC-P-${student.id}-${index + 1}`,
      )));
    expect(responses.filter(response => response.status() === 201)).toHaveLength(1);
    expect(responses.filter(response => response.status() === 400)).toHaveLength(3);

    const entries = await (await treasurerApi.get('/api/finance/ledger')).json();
    const studentEntries = entries.filter(entry => entry.student.id === student.id);
    expect(studentEntries).toHaveLength(2);
    const remaining = studentEntries.reduce(
      (sum, entry) => sum + (entry.kind === 'FINE' ? Number(entry.amount) : -Number(entry.amount)),
      0,
    );
    expect(remaining).toBe(40);
    const summary = await linkedStudentApi.get('/api/students/me/summary');
    expect(summary.status()).toBe(200);
    expect(Number((await summary.json()).unpaidBalance)).toBe(40);
    await linkedStudentApi.dispose();
  } finally {
    await adminApi.dispose();
    await treasurerApi.dispose();
    await officerApi.dispose();
    await studentApi.dispose();
  }
});
