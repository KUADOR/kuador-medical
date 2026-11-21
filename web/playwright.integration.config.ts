// End-to-end walk of the web against the REAL api (MEDK-75): no simulated API.
// Opt-in and local only: it needs a running api (perfil local), Mailpit and the web dev server
// proxying /api to that api. It is not part of `npm test` nor of CI. See e2e/integration/README.md.
import { defineConfig, devices } from '@playwright/test';

const baseURL = process.env['E2E_WEB_URL'] ?? 'http://localhost:4200';

export default defineConfig({
  testDir: './e2e/integration',
  testMatch: '**/*.integration.ts',
  // The walk is one ordered story over shared server state.
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 180_000,
  reporter: 'list',
  use: {
    baseURL,
    locale: 'es-EC',
    timezoneId: 'America/Guayaquil',
    trace: 'retain-on-failure',
    ...devices['Desktop Chrome'],
  },
});
