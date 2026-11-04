import { readFile } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
import { expect, test } from '@playwright/test';

import {
  addFundEntry,
  addLedgerEntry,
  authenticatedApi,
  navigate,
  signIn,
  signOut,
  testPassword,
} from './financial-helpers.js';

test('reconciles dashboard, reports, exports, permissions, and PostgreSQL-backed records', async ({ page }) => {
  test.setTimeout(90_000);
  const marker = `RPT-${randomUUID().replaceAll('-', '').slice(0, 12).toUpperCase()}`;
  const adminPassword = testPassword('ADMIN');
  const adminApi = await authenticatedApi('admin', adminPassword);
  const roleApis = [];

  try {
    const baselineDashboard = await (await adminApi.get('/api/dashboard')).json();
    const baselineStudents = await (await adminApi.get('/api/students')).json();
    const baselineEvents = await (await adminApi.get('/api/attendance/events')).json();
    const baselineAttendance = await (await adminApi.get('/api/attendance/records')).json();
    const activeEventIds = new Set(baselineEvents.filter(event => event.active).map(event => event.id));
    const baselinePresentOrLate = baselineAttendance.filter(record =>
      activeEventIds.has(record.event?.id) && ['PRESENT', 'LATE'].includes(record.status)).length;
    const expectedAttendanceRate = Math.round(
      ((baselinePresentOrLate + 2) / ((activeEventIds.size + 1) * (baselineStudents.length + 4))) * 1000,
    ) / 10;
    const formatMoney = value => `₱${Number(value).toLocaleString('en-PH', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })}`;

    const students = [];
    for (const [label, fullName] of [
      ['PARTIAL', `${marker} Élodie "Ace", Doe`],
      ['FULL', '=1+1'],
      ['ABSENT', `${marker} Absent`],
      ['EXCUSED', `${marker} Excused`],
    ]) {
      const response = await adminApi.post('/api/students', {
        data: {
          studentNumber: `${marker}-${label}`,
          fullName,
          course: 'Fictional Course',
          yearLevel: '2',
          section: 'R',
        },
      });
      expect(response.status()).toBe(201);
      students.push(await response.json());
    }

    expect((await addLedgerEntry(adminApi, students[0].id, 'FINE', 100, `${marker} fine`, `${marker}-FINE`)).status()).toBe(201);
    expect((await addLedgerEntry(adminApi, students[0].id, 'PAYMENT', 40, `${marker} partial payment`, `${marker}-PAY-PART`)).status()).toBe(201);
    expect((await addFundEntry(adminApi, 'OPENING_BALANCE', 1000, `${marker} income`)).status()).toBe(201);
    expect((await addFundEntry(adminApi, 'DISBURSEMENT', 250, `${marker} expense one`)).status()).toBe(201);
    expect((await addFundEntry(adminApi, 'DISBURSEMENT', 150, `${marker} expense two`)).status()).toBe(201);

    const eventResponse = await adminApi.post('/api/attendance/events', {
      data: { title: `${marker} attendance`, eventDate: '2026-11-02', active: true },
    });
    expect(eventResponse.status()).toBe(201);
    const event = await eventResponse.json();
    for (const [index, status] of ['PRESENT', 'LATE', 'ABSENT', 'EXCUSED'].entries()) {
      const response = await adminApi.post('/api/attendance/scan', {
        data: { eventId: event.id, studentNumber: students[index].studentNumber, status },
      });
      expect(response.status()).toBe(201);
    }
    const duplicate = await adminApi.post('/api/attendance/scan', {
      data: { eventId: event.id, studentNumber: students[0].studentNumber, status: 'PRESENT' },
    });
    expect(duplicate.status()).toBe(409);

    const initialDashboard = await (await adminApi.get('/api/dashboard')).json();
    expect(initialDashboard.students).toBe(baselineStudents.length + 4);
    expect(Number(initialDashboard.collections)).toBe(Number(baselineDashboard.collections) + 40);
    expect(Number(initialDashboard.unpaid)).toBe(Number(baselineDashboard.unpaid) + 60);
    expect(Number(initialDashboard.fundBalance)).toBe(Number(baselineDashboard.fundBalance) + 600);
    expect(initialDashboard.attendanceScans).toBe(baselineDashboard.attendanceScans + 4);
    expect(initialDashboard.attendanceRate).toBe(expectedAttendanceRate);
    expect(initialDashboard.recent.some(row => row.description === `${marker} partial payment`)).toBe(true);

    const finance = await (await adminApi.get('/api/reports/finance')).json();
    expect(Number(finance.totalCollections)).toBe(Number(baselineDashboard.collections) + 40);
    expect(finance.transactions.filter(row => row.description.startsWith(marker))).toHaveLength(2);
    expect(finance.transactions.filter(row => row.description.startsWith(marker)).map(row => row.kind))
      .toEqual(['PAYMENT', 'FINE']);
    expect(Number(finance.balances.find(row => row.studentNumber === students[0].studentNumber).unpaidBalance)).toBe(60);

    const attendanceRows = await (await adminApi.get('/api/reports/attendance')).json();
    const attendanceReport = attendanceRows.find(row => row.eventId === event.id);
    expect(attendanceReport).toMatchObject({
      total: 4,
      eventDate: '2026-11-02',
      present: 1,
      late: 1,
      absent: 1,
      excused: 1,
    });
    const attendanceRecords = await (await adminApi.get('/api/attendance/records')).json();
    const eventRecords = attendanceRecords.filter(row => row.event?.id === event.id);
    expect(eventRecords).toHaveLength(4);
    expect(eventRecords.every(row => row.student && students.some(student => student.id === row.student.id))).toBe(true);
    expect(eventRecords.every(row => row.scannedAt && row.scannedAt.startsWith('2026-11-03'))).toBe(true);

    await signIn(page, 'admin', adminPassword);
    await expect(page.locator('.metric-card').filter({ hasText: 'Total payments collected' }))
      .toContainText(formatMoney(Number(baselineDashboard.collections) + 40));
    await expect(page.locator('.metric-card').filter({ hasText: 'Fund balance' }))
      .toContainText(formatMoney(Number(baselineDashboard.fundBalance) + 600));
    await expect(page.locator('.metric-card').filter({ hasText: 'Unpaid balances' }))
      .toContainText(formatMoney(Number(baselineDashboard.unpaid) + 60));
    await expect(page.locator('.metric-card').filter({ hasText: 'Attendance rate' }))
      .toContainText(`${expectedAttendanceRate}%`);

    await navigate(page, 'Reports');
    const reportSelector = page.getByRole('combobox', { name: 'Report' });
    const chooseReport = async name => {
      await reportSelector.press('ArrowDown');
      await page.getByRole('option', { name }).click();
    };

    await expect(page.getByRole('row').filter({ hasText: students[0].studentNumber })).toBeVisible();
    await expect(page.locator('thead th')).toHaveText(['Student ID', 'Full name', 'Course', 'Year level', 'Section']);
    const displayedStudentNumbers = await page.locator('tbody tr td:first-child').allTextContents();
    expect(displayedStudentNumbers).toEqual([...displayedStudentNumbers].sort());
    for (const student of students) expect(displayedStudentNumbers).toContain(student.studentNumber);
    await expect(page.getByRole('row').filter({ hasText: students[0].studentNumber })).toBeVisible();
    let downloadWait = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Download CSV for Excel' }).click();
    let download = await downloadWait;
    expect(download.suggestedFilename()).toBe('students.csv');
    let csv = await readFile(await download.path(), 'utf8');
    expect(csv).toContain('"studentNumber","fullName","course","yearLevel","section"');
    expect(csv).toContain(`${marker} Élodie ""Ace"", Doe`);
    expect(csv).toContain(`"'=1+1"`);

    await chooseReport('Student fines and payments');
    await expect(page.locator('thead th')).toHaveText(['Date', 'Student', 'Type', 'Amount', 'Reference', 'Description']);
    await expect(page.getByRole('row').filter({ hasText: `${marker} partial payment` })).toBeVisible();
    downloadWait = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Download CSV for Excel' }).click();
    download = await downloadWait;
    csv = await readFile(await download.path(), 'utf8');
    expect(csv).toContain('"date","student","kind","amount","reference","description"');
    expect(csv).toContain(`${marker}-PAY-PART`);
    expect(csv).toContain('"40.00"');

    await chooseReport('Unpaid balances');
    await expect(page.locator('thead th')).toHaveText(['Student ID', 'Student Name', 'Unpaid Balance']);
    await expect(page.getByRole('row').filter({ hasText: students[0].studentNumber })).toContainText('₱60.00');
    downloadWait = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Download CSV for Excel' }).click();
    download = await downloadWait;
    csv = await readFile(await download.path(), 'utf8');
    expect(csv).toContain('"studentNumber","fullName","unpaidBalance"');
    expect(csv).toContain('"60.00"');

    await chooseReport('Department funds and expenses');
    await expect(page.locator('thead th')).toHaveText(['Date', 'Type', 'Amount', 'Description']);
    await expect(page.getByRole('row').filter({ hasText: `${marker} expense one` })).toBeVisible();
    downloadWait = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Download CSV for Excel' }).click();
    download = await downloadWait;
    csv = await readFile(await download.path(), 'utf8');
    expect(csv).toContain('"createdAt","type","amount","description"');
    expect(csv).toContain(`${marker} income`);
    expect(csv).toContain(`${marker} expense two`);

    await chooseReport('Attendance by activity');
    await expect(page.locator('thead th')).toHaveText(['Date', 'Activity', 'Present', 'Late', 'Absent', 'Excused', 'Total']);
    await expect(page.getByRole('row').filter({ hasText: `${marker} attendance` }))
      .toContainText('1');
    downloadWait = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Download CSV for Excel' }).click();
    download = await downloadWait;
    csv = await readFile(await download.path(), 'utf8');
    expect(csv).toContain('"eventDate","title","present","late","absent","excused","total"');
    expect(csv).toContain(`${marker} attendance`);

    await page.evaluate(() => {
      window.print = () => { window.__reportPrintCalled = true; };
    });
    await page.getByRole('button', { name: 'Print or save as PDF' }).click();
    expect(await page.evaluate(() => window.__reportPrintCalled)).toBe(true);
    await page.emulateMedia({ media: 'print' });
    await expect(page.locator('.no-print')).toHaveCSS('display', 'none');
    await expect(page.locator('.printable-report')).toBeVisible();
    await page.emulateMedia({ media: 'screen' });

    for (const role of ['TREASURER', 'OFFICER']) {
      const financialAccess = role === 'TREASURER';
      const api = await authenticatedApi(role.toLowerCase(), testPassword(role));
      roleApis.push(api);
      const dashboard = await (await api.get('/api/dashboard')).json();
      if (financialAccess) {
        expect(Number(dashboard.collections)).toBe(Number(baselineDashboard.collections) + 40);
        expect(Number(dashboard.unpaid)).toBe(Number(baselineDashboard.unpaid) + 60);
        expect(Number(dashboard.fundBalance)).toBe(Number(baselineDashboard.fundBalance) + 600);
        expect(dashboard.attendanceRate).toBeUndefined();
        expect((await api.get('/api/reports/finance')).status()).toBe(200);
        expect((await api.get('/api/reports/funds')).status()).toBe(200);
        expect((await api.get('/api/reports/attendance')).status()).toBe(403);
      } else {
        expect(dashboard.collections).toBeUndefined();
        expect(dashboard.unpaid).toBeUndefined();
        expect(dashboard.fundBalance).toBeUndefined();
        expect(Number(dashboard.attendanceScans)).toBe(baselineDashboard.attendanceScans + 4);
        expect(Number(dashboard.attendanceRate)).toBe(expectedAttendanceRate);
        expect((await api.get('/api/reports/finance')).status()).toBe(403);
        expect((await api.get('/api/reports/funds')).status()).toBe(403);
        expect((await api.get('/api/reports/attendance')).status()).toBe(200);
      }
      expect((await api.get('/api/reports/summary')).status()).toBe(200);
      expect((await api.get('/api/reports/students')).status()).toBe(200);

      await signOut(page);
      await signIn(page, role.toLowerCase(), testPassword(role));
      const paymentMetric = page.locator('.metric-card').filter({ hasText: 'Total payments collected' });
      const attendanceMetric = page.locator('.metric-card').filter({ hasText: 'Attendance rate' });
      if (financialAccess) {
        await expect(paymentMetric).toContainText(formatMoney(Number(baselineDashboard.collections) + 40));
        await expect(attendanceMetric).toHaveCount(0);
      } else {
        await expect(paymentMetric).toHaveCount(0);
        await expect(attendanceMetric).toBeVisible();
      }
      await navigate(page, 'Dashboard');
      await page.getByRole('button', { name: 'Refresh' }).click();
      const unpaidMetric = page.locator('.metric-card').filter({ hasText: 'Unpaid balances' });
      if (financialAccess) {
        await expect(unpaidMetric).toContainText(formatMoney(Number(baselineDashboard.unpaid) + 60));
      } else {
        await expect(unpaidMetric).toHaveCount(0);
      }
    }

    const fullFine = await addLedgerEntry(adminApi, students[1].id, 'FINE', 50, `${marker} paid fine`, `${marker}-FINE-FULL`);
    expect(fullFine.status()).toBe(201);
    const fullPayment = await addLedgerEntry(adminApi, students[1].id, 'PAYMENT', 50, `${marker} full payment`, `${marker}-PAY-FULL`);
    expect(fullPayment.status()).toBe(201);
    const afterFullPayment = await (await adminApi.get('/api/dashboard')).json();
    expect(Number(afterFullPayment.collections)).toBe(Number(baselineDashboard.collections) + 90);
    expect(Number(afterFullPayment.unpaid)).toBe(Number(baselineDashboard.unpaid) + 60);
    expect(Number((await (await adminApi.get('/api/reports/finance')).json()).totalCollections))
      .toBe(Number(baselineDashboard.collections) + 90);

    await page.reload();
    await signIn(page, 'admin', adminPassword);
    await expect(page.locator('.metric-card').filter({ hasText: 'Total payments collected' }))
      .toContainText(formatMoney(Number(baselineDashboard.collections) + 90));
  } finally {
    await adminApi.dispose();
    await Promise.all(roleApis.map(api => api.dispose()));
  }
});
