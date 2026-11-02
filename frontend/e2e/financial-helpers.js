import { randomUUID } from 'node:crypto';
import { request as requestFactory } from '@playwright/test';

export const apiBaseUrl = process.env.TRACK2_E2E_API_URL || 'http://127.0.0.1:8082/api';

export function testPassword(role) {
  const password = process.env[`TRACK2_E2E_${role}_PASSWORD`];
  if (!password) {
    throw new Error(`TRACK2_E2E_${role}_PASSWORD must be set for isolated financial tests.`);
  }
  return password;
}

export async function authenticatedApi(username, password) {
  const token = Buffer.from(`${username}:${password}`).toString('base64');
  return requestFactory.newContext({
    baseURL: new URL(apiBaseUrl).origin,
    extraHTTPHeaders: { Authorization: `Basic ${token}` },
  });
}

export async function createStudent(api, label = 'Finance') {
  const suffix = randomUUID().replaceAll('-', '').slice(0, 12).toUpperCase();
  const student = {
    studentNumber: `E2E-${suffix}`,
    fullName: `${label} ${suffix}`,
    course: 'Test Course',
    yearLevel: '2',
    section: 'A',
  };
  const response = await api.post('/api/students', { data: student });
  if (response.status() !== 201) {
    throw new Error(`Student setup failed with HTTP ${response.status()}: ${await response.text()}`);
  }
  return response.json();
}

export async function addLedgerEntry(api, studentId, kind, amount, description, referenceNumber) {
  return api.post('/api/finance/ledger', {
    data: { studentId, kind, amount, description, referenceNumber },
  });
}

export async function addFundEntry(api, type, amount, description) {
  return api.post('/api/finance/funds', {
    data: { type, amount, description },
  });
}

export async function signIn(page, username, password) {
  await page.goto('/');
  await page.getByRole('textbox', { name: 'Username' }).fill(username);
  await page.getByRole('textbox', { name: 'Password' }).fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await page.getByRole('button', { name: 'Refresh' }).waitFor();
}

export async function signOut(page) {
  await page.getByRole('button', { name: 'Open profile menu' }).click();
  await page.getByText('Log out', { exact: true }).click();
}

export async function navigate(page, label) {
  await page.getByText(label, { exact: true }).first().evaluate(element => element.click());
}

export async function selectFundType(page, label) {
  await page.getByRole('combobox', { name: 'Entry type' }).press('ArrowDown');
  await page.getByRole('option', { name: label }).click();
}

export async function selectStudent(page, student) {
  await page.getByRole('combobox', { name: 'Student' }).press('ArrowDown');
  await page.getByRole('option', {
    name: `${student.fullName} (${student.studentNumber})`,
  }).click();
}
