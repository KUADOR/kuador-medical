// Visual regression tests of the web piece (MEDK-33 plan: visual tests, risk R5).
// They run against the dev server with a simulated API (page.route) and synthetic data only:
// no real API, no tenant data. Baselines are generated in the Playwright Linux container
// (`npm run test:visual:docker`), the same image CI uses, so fonts and antialiasing match.
import { defineConfig, devices } from '@playwright/test';

const PORT = Number(process.env['VISUAL_PORT'] ?? 4321);
const baseURL = `http://localhost:${PORT}`;

export default defineConfig({
  testDir: './e2e/visual',
  testMatch: '**/*.visual.ts',
  snapshotPathTemplate: '{testDir}/__screenshots__/{testFilePath}/{arg}-{projectName}-{platform}{ext}',
  fullyParallel: true,
  forbidOnly: !!process.env['CI'],
  retries: 0,
  reporter: process.env['CI'] ? [['list'], ['html', { open: 'never' }]] : 'list',
  expect: {
    toHaveScreenshot: {
      animations: 'disabled',
      caret: 'hide',
      // Bounded tolerance for antialiasing differences (R5).
      maxDiffPixelRatio: 0.01,
    },
  },
  use: {
    baseURL,
    locale: 'es-EC',
    timezoneId: 'America/Guayaquil',
    reducedMotion: 'reduce',
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'light', use: { ...devices['Desktop Chrome'], colorScheme: 'light' } },
    { name: 'dark', use: { ...devices['Desktop Chrome'], colorScheme: 'dark' } },
    { name: 'mobile-light', use: { ...devices['Pixel 7'], colorScheme: 'light' } },
  ],
  webServer: {
    command: `npx ng serve --port ${PORT}`,
    url: baseURL,
    reuseExistingServer: !process.env['CI'],
    timeout: 180_000,
  },
});
