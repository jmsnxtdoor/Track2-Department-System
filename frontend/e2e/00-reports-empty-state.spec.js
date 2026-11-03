import { expect, test } from '@playwright/test';

import {
  authenticatedApi,
  navigate,
  signIn,
  testPassword,
} from './financial-helpers.js';

test('shows clear empty dashboard and report states, and protects report APIs', async ({ page }) => {
  const adminPassword = testPassword('ADMIN');
  await signIn(page, 'admin', adminPassword);
  await expect(page.getByText('No payment activity for this period.')).toBeVisible();
  await expect(page.getByText('No recent payments or fines.')).toBeVisible();

  await navigate(page, 'Reports');
  await expect(page.getByText('No records for this report.')).toBeVisible();
  await page.getByRole('button', { name: 'Download CSV for Excel' }).click();
  await expect(page.getByText('There is no data in this report to download.')).toBeVisible();

  const adminApi = await authenticatedApi('admin', adminPassword);
  const studentApi = await authenticatedApi('student', testPassword('STUDENT'));
  const anonymousApi = await authenticatedApi('', '');
  try {
    for (const endpoint of ['/dashboard', '/reports/summary', '/reports/students', '/reports/finance', '/reports/funds', '/reports/attendance']) {
      expect((await adminApi.get(`/api${endpoint}`)).status()).toBe(200);
      expect((await studentApi.get(`/api${endpoint}`)).status()).toBe(403);
      expect((await anonymousApi.get(`/api${endpoint}`)).status()).toBe(401);
    }
    expect((await adminApi.get('/api/reports/unknown')).status()).toBe(404);
  } finally {
    await adminApi.dispose();
    await studentApi.dispose();
    await anonymousApi.dispose();
  }
});

test('keeps a dashboard API failure visible while its parallel requests complete', async ({ page }) => {
  await page.route('**/api/dashboard', route => route.fulfill({
    status: 503,
    contentType: 'application/json',
    body: JSON.stringify({ detail: 'Synthetic dashboard failure.' }),
  }));
  await signIn(page, 'admin', testPassword('ADMIN'));
  await expect(page.getByRole('alert')).toContainText('Synthetic dashboard failure.');
});
