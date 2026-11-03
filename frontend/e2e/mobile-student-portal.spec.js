import { randomUUID } from 'node:crypto';
import { expect, test } from '@playwright/test';

import {
  apiBaseUrl,
  authenticatedApi,
  createStudent,
  testPassword,
} from './financial-helpers.js';

const unique = () => randomUUID().replaceAll('-', '').slice(0, 12).toUpperCase();

async function setFlutterText(page, name, value) {
  const field = page.getByRole('textbox', { name });
  await field.click({ force: true });
  await field.press('Control+A');
  await field.pressSequentially(value);
}

test('student portal shows linked profile, balance, QR, and attendance after login and refresh', async ({ page, request }) => {
  test.setTimeout(120_000);
  const studentPassword = testPassword('STUDENT');
  const adminApi = await authenticatedApi('admin', testPassword('ADMIN'));
  const treasurerApi = await authenticatedApi('treasurer', testPassword('TREASURER'));
  const officerApi = await authenticatedApi('officer', testPassword('OFFICER'));
  const studentSuffix = unique();
  const username = `portal-${studentSuffix.toLowerCase()}`;
  const flutterUrl = process.env.TRACK2_E2E_FLUTTER_URL;

  if (!flutterUrl) {
    throw new Error('TRACK2_E2E_FLUTTER_URL must point to the isolated Flutter Web app.');
  }

  try {
    const student = await createStudent(adminApi, 'Portal');
    const accountResponse = await adminApi.post('/api/admin/users', {
      data: { username, role: 'STUDENT', password: studentPassword },
    });
    expect(accountResponse.status()).toBe(201);
    const account = await accountResponse.json();
    expect((await adminApi.put(`/api/admin/users/${account.id}/student-link`, {
      data: { studentId: student.id },
    })).status()).toBe(200);

    expect((await adminApi.post('/api/finance/ledger', {
      data: {
        studentId: student.id,
        kind: 'FINE',
        amount: 100,
        description: 'Mobile portal test fine',
        referenceNumber: `PORTAL-FINE-${studentSuffix}`,
      },
    })).status()).toBe(201);
    expect((await treasurerApi.post('/api/finance/ledger', {
      data: {
        studentId: student.id,
        kind: 'PAYMENT',
        amount: 40,
        description: 'Mobile portal test payment',
        referenceNumber: `PORTAL-PAY-${studentSuffix}`,
      },
    })).status()).toBe(201);

    const eventTitle = `Mobile portal event ${studentSuffix}`;
    const eventResponse = await officerApi.post('/api/attendance/events', {
      data: {
        title: eventTitle,
        eventDate: new Date().toISOString().slice(0, 10),
        active: true,
      },
    });
    expect(eventResponse.status()).toBe(201);
    const event = await eventResponse.json();
    const eventDateLabel = new Intl.DateTimeFormat('en-US', {
      timeZone: 'UTC',
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    }).format(new Date(`${event.eventDate}T00:00:00Z`));
    const scanResponse = await officerApi.post('/api/attendance/scan', {
      data: {
        eventId: event.id,
        studentNumber: student.studentNumber,
        status: 'PRESENT',
      },
    });
    expect(scanResponse.status()).toBe(201);
    const scan = await scanResponse.json();

    const studentApi = await authenticatedApi(username, studentPassword);
    try {
      const summaryResponse = await studentApi.get('/api/students/me/summary');
      expect(summaryResponse.status()).toBe(200);
      const summary = await summaryResponse.json();
      expect(summary.student.studentNumber).toBe(student.studentNumber);
      expect(Number(summary.unpaidBalance)).toBe(60);

      const ledgerResponse = await studentApi.get('/api/students/me/ledger');
      expect(ledgerResponse.status()).toBe(200);
      expect((await ledgerResponse.json()).map(entry => [entry.kind, Number(entry.amount)]))
        .toEqual(expect.arrayContaining([['FINE', 100], ['PAYMENT', 40]]));
      const historyResponse = await studentApi.get('/api/students/me/attendance');
      expect(historyResponse.status()).toBe(200);
      expect((await historyResponse.json()).find(record => record.activityName === eventTitle))
        .toEqual({
          activityName: eventTitle,
          activityDate: event.eventDate,
          scannedAt: scan.scannedAt,
          status: 'PRESENT',
        });
      expect((await studentApi.get(`/api/students/${student.id}`)).status()).toBe(403);
    } finally {
      await studentApi.dispose();
    }

    expect((await request.get(`${apiBaseUrl}/students/me/summary`)).status()).toBe(401);
    await page.setViewportSize({ width: 390, height: 844 });
    const flutterPage = await page.context().newPage();
    try {
      await flutterPage.setViewportSize({ width: 390, height: 844 });
      await flutterPage.goto(flutterUrl, { waitUntil: 'domcontentloaded' });
      const semanticsPlaceholder = flutterPage.locator('flt-semantics-placeholder');
      await semanticsPlaceholder.waitFor({ state: 'attached' });
      await semanticsPlaceholder.evaluate(element => element.click());
      await setFlutterText(flutterPage, 'API server URL', apiBaseUrl);
      await setFlutterText(flutterPage, 'Username', username);
      await setFlutterText(flutterPage, 'Password', 'invalid-password');
      await flutterPage.getByRole('button', { name: 'Sign in' }).click();
      await expect(flutterPage.getByText('Username or password is incorrect. Try again.'))
        .toBeVisible();

      await setFlutterText(flutterPage, 'Password', studentPassword);
      await flutterPage.getByRole('button', { name: 'Sign in' }).click();
      await expect(flutterPage.getByRole('group', {
        name: new RegExp(student.fullName),
      })).toBeVisible();
      await expect(flutterPage.getByText('₱60.00')).toBeVisible();
      await expect(flutterPage.getByText('1 present · 0 late')).toBeVisible();

      await flutterPage.getByRole('tab', { name: 'Fines' }).click();
      await expect(flutterPage.getByText('Mobile portal test fine')).toBeVisible();
      await expect(flutterPage.getByText('₱100.00')).toBeVisible();
      await flutterPage.getByRole('tab', { name: 'Payments' }).click();
      await expect(flutterPage.getByText('Mobile portal test payment')).toBeVisible();
      await expect(flutterPage.getByText('₱40.00')).toBeVisible();
      await flutterPage.getByRole('tab', { name: 'Attendance' }).click();
      await expect(flutterPage.getByText(eventTitle)).toBeVisible();
      await expect(flutterPage.getByText('Present')).toBeVisible();
      await expect(flutterPage.getByText(eventDateLabel)).toBeVisible();
      await expect(flutterPage.getByText(/Checked in/)).toBeVisible();

      await flutterPage.getByRole('tab', { name: 'Profile' }).click();
      const studentIdLabel = new RegExp(`Student ID\\s*:?\\s*${student.studentNumber}`);
      await expect(flutterPage.getByText(studentIdLabel)).toBeVisible();
      await expect(flutterPage.getByText(new RegExp(`Course\\s+${student.course}`)))
        .toBeVisible();
      await expect(flutterPage.getByText(new RegExp(`Year level\\s+${student.yearLevel}`)))
        .toBeVisible();
      await expect(flutterPage.getByText(new RegExp(`Section\\s+${student.section}`)))
        .toBeVisible();
      await flutterPage.getByText('My QR code').click();
      await expect(flutterPage.getByText(studentIdLabel)).toBeVisible();
      await flutterPage.getByRole('button', { name: 'Back' }).click();
      await flutterPage.getByRole('tab', { name: 'Profile' }).click();
      await flutterPage.getByText('Log out', { exact: true }).click();
      await expect(flutterPage.getByRole('button', { name: 'Sign in' })).toBeVisible();

      await setFlutterText(flutterPage, 'Username', username);
      await setFlutterText(flutterPage, 'Password', studentPassword);
      await flutterPage.getByRole('button', { name: 'Sign in' }).click();
      await expect(flutterPage.getByRole('group', {
        name: new RegExp(student.fullName),
      })).toBeVisible();
      await flutterPage.getByRole('button', { name: 'Refresh' }).click();
      await expect(flutterPage.getByText('₱60.00')).toBeVisible();
    } finally {
      await flutterPage.close();
    }
  } finally {
    await adminApi.dispose();
    await treasurerApi.dispose();
    await officerApi.dispose();
  }
});
