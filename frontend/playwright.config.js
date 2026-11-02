import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: 'list',
  use: {
    baseURL: process.env.TRACK2_E2E_WEB_URL || 'http://127.0.0.1:5175',
    headless: true,
    viewport: { width: 1440, height: 1100 },
    trace: 'retain-on-failure',
  },
});
