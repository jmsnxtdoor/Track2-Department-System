import { randomUUID } from 'node:crypto';
import { expect, test } from '@playwright/test';

import {
  apiBaseUrl,
  authenticatedApi,
  createStudent,
  navigate,
  signIn,
  testPassword,
} from './financial-helpers.js';

const unique = () => randomUUID().replaceAll('-', '').slice(0, 12).toUpperCase();

async function createLinkedStudent(adminApi) {
  const student = await createStudent(adminApi, 'Attendance');
  const password = testPassword('STUDENT');
  const response = await adminApi.post('/api/admin/users', {
    data: {
      username: `attendance-${student.studentNumber.toLowerCase()}`,
      role: 'STUDENT',
      password,
    },
  });
  expect(response.status()).toBe(201);
  const account = await response.json();
  const link = await adminApi.put(`/api/admin/users/${account.id}/student-link`, {
    data: { studentId: student.id },
  });
  expect(link.status()).toBe(200);
  return {
    student,
    username: account.username,
    password,
  };
}

async function createEvent(api, title) {
  const response = await api.post('/api/attendance/events', {
    data: { title, eventDate: new Date().toISOString().slice(0, 10), active: true },
  });
  expect(response.status()).toBe(201);
  return response.json();
}

test('records a student QR payload in Vue and keeps server history consistent after refresh', async ({ page }) => {
  test.setTimeout(120_000);
  const adminPassword = testPassword('ADMIN');
  const adminApi = await authenticatedApi('admin', adminPassword);

  try {
    const linked = await createLinkedStudent(adminApi);
    const eventTitle = `E2E Attendance ${unique()}`;
    const eventNameInput = page.getByRole('textbox', { name: 'Activity name' });
    const eventDate = new Date().toISOString().slice(0, 10);
    const startedAt = Date.now();

    await signIn(page, 'admin', adminPassword);
    await navigate(page, 'Attendance');
    await eventNameInput.fill(eventTitle);
    await page.getByRole('textbox', { name: 'Date' }).fill(eventDate);
    const createResponse = page.waitForResponse(response =>
      response.url().endsWith('/api/attendance/events')
      && response.request().method() === 'POST');
    await page.getByRole('button', { name: 'Create activity' }).click();
    expect((await createResponse).status()).toBe(201);
    await expect(page.getByText('Attendance activity created.', { exact: true })).toBeVisible();

    const eventResponse = await adminApi.get('/api/attendance/events');
    expect(eventResponse.status()).toBe(200);
    const event = (await eventResponse.json()).find(item => item.title === eventTitle);
    expect(event).toBeTruthy();

    await page.getByRole('combobox', { name: 'Activity' }).press('ArrowDown');
    await page.getByRole('option', { name: new RegExp(eventTitle) }).click();
    await page.getByRole('textbox', { name: 'Student ID or scanned QR code' })
      .fill(linked.student.studentNumber);
    await page.getByRole('combobox', { name: 'Status' }).press('ArrowDown');
    await page.getByRole('option', { name: 'Late' }).click();
    const scanResponse = page.waitForResponse(response =>
      response.url().endsWith('/api/attendance/records')
      && response.request().method() === 'POST');
    await page.getByRole('button', { name: 'Record attendance' }).click();
    const scan = await scanResponse;
    expect(scan.status()).toBe(201);
    const record = await scan.json();
    expect(record.student.studentNumber).toBe(linked.student.studentNumber);
    expect(record.event.id).toBe(event.id);
    expect(record.status).toBe('LATE');
    const timestamp = new Date(record.scannedAt).getTime();
    expect(Number.isFinite(timestamp)).toBe(true);
    expect(timestamp).toBeGreaterThanOrEqual(startedAt - 5_000);
    expect(timestamp).toBeLessThanOrEqual(Date.now() + 5_000);
    await expect(page.getByRole('row').filter({ hasText: linked.student.studentNumber }))
      .toContainText('Late');
    await expect(page.getByRole('row').filter({ hasText: eventTitle }))
      .toContainText(record.scannedAt);

    const duplicate = await adminApi.post('/api/attendance/scan', {
      data: { eventId: event.id, studentNumber: linked.student.studentNumber, status: 'PRESENT' },
    });
    expect(duplicate.status()).toBe(409);
    expect((await duplicate.json()).detail).toBe('Already checked in.');

    const studentHistoryApi = await authenticatedApi(linked.username, linked.password);
    try {
      const history = await studentHistoryApi.get('/api/students/me/attendance');
      expect(history.status()).toBe(200);
      const studentRecord = (await history.json()).find(item => item.activityName === eventTitle);
      expect(studentRecord).toEqual({
        activityName: eventTitle,
        activityDate: event.eventDate,
        scannedAt: record.scannedAt,
        status: 'LATE',
      });

      await page.reload();
      await signIn(page, 'admin', adminPassword);
      await navigate(page, 'Attendance');
      const persistedRow = page.getByRole('row').filter({ hasText: linked.student.studentNumber });
      await expect(persistedRow).toContainText(eventTitle);
      await expect(persistedRow).toContainText(record.scannedAt);
      await expect(persistedRow).toContainText('Late');

      const flutterUrl = process.env.TRACK2_E2E_FLUTTER_URL;
      if (!flutterUrl) {
        throw new Error('TRACK2_E2E_FLUTTER_URL must point to the isolated Flutter Web app.');
      }
      const flutterPage = await page.context().newPage();
      try {
        await flutterPage.goto(flutterUrl, { waitUntil: 'domcontentloaded' });
        const semanticsPlaceholder = flutterPage.locator('flt-semantics-placeholder');
        await semanticsPlaceholder.waitFor({ state: 'attached' });
        await semanticsPlaceholder.evaluate(element => element.click());
        await flutterPage.getByRole('textbox', { name: 'API server URL' })
          .fill(apiBaseUrl);
        const username = flutterPage.getByRole('textbox', { name: 'Username' });
        await username.click({ force: true });
        await username.pressSequentially(linked.username);
        const password = flutterPage.getByRole('textbox', { name: 'Password' });
        await password.click({ force: true });
        await password.pressSequentially(linked.password);
        await flutterPage.getByRole('button', { name: 'Sign in' }).click();
        await flutterPage.getByRole('tab', { name: 'Attendance' }).click();
        await expect(flutterPage.getByText(eventTitle)).toBeVisible();
        await expect(flutterPage.getByText('Late')).toBeVisible();
        await expect(flutterPage.getByText(/Checked in/)).toBeVisible();
      } finally {
        await flutterPage.close();
      }
    } finally {
      await studentHistoryApi.dispose();
    }
  } finally {
    await adminApi.dispose();
  }
});

test('enforces attendance roles and rejects invalid and concurrent QR scans', async () => {
  test.setTimeout(90_000);
  const admin = await authenticatedApi('admin', testPassword('ADMIN'));
  const officer = await authenticatedApi('officer', testPassword('OFFICER'));
  const treasurer = await authenticatedApi('treasurer', testPassword('TREASURER'));
  const student = await authenticatedApi('student', testPassword('STUDENT'));

  try {
    expect((await admin.get('/api/attendance/events')).status()).toBe(200);
    expect((await officer.get('/api/attendance/events')).status()).toBe(200);
    expect((await treasurer.get('/api/attendance/events')).status()).toBe(403);
    expect((await student.get('/api/attendance/events')).status()).toBe(403);

    const unauthorizedEvent = {
      title: `Unauthorized attendance ${unique()}`,
      eventDate: new Date().toISOString().slice(0, 10),
      active: true,
    };
    expect((await treasurer.post('/api/attendance/events', { data: unauthorizedEvent })).status()).toBe(403);
    expect((await student.post('/api/attendance/events', { data: unauthorizedEvent })).status()).toBe(403);

    const linked = await createLinkedStudent(admin);
    const event = await createEvent(officer, `E2E Concurrent Attendance ${unique()}`);
    expect((await admin.post('/api/attendance/scan', {
      data: { eventId: event.id, studentNumber: 'INVALID-' + unique(), status: 'PRESENT' },
    })).status()).toBe(404);
    expect((await admin.post('/api/attendance/scan', {
      data: { eventId: event.id + 9_000_000_000, studentNumber: linked.student.studentNumber, status: 'PRESENT' },
    })).status()).toBe(404);
    expect((await treasurer.post('/api/attendance/scan', {
      data: { eventId: event.id, studentNumber: linked.student.studentNumber, status: 'PRESENT' },
    })).status()).toBe(403);
    expect((await student.post('/api/attendance/scan', {
      data: { eventId: event.id, studentNumber: linked.student.studentNumber, status: 'PRESENT' },
    })).status()).toBe(403);

    const scans = await Promise.all(Array.from({ length: 6 }, () =>
      officer.post('/api/attendance/scan', {
        data: { eventId: event.id, studentNumber: linked.student.studentNumber, status: 'PRESENT' },
      })));
    expect(scans.filter(response => response.status() === 201)).toHaveLength(1);
    expect(scans.filter(response => response.status() === 409)).toHaveLength(5);

    const records = await (await officer.get('/api/attendance/records')).json();
    expect(records.filter(record =>
      record.event.id === event.id && record.student.id === linked.student.id
    )).toHaveLength(1);
  } finally {
    await admin.dispose();
    await officer.dispose();
    await treasurer.dispose();
    await student.dispose();
  }
});
