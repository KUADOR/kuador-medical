// Simulated API for the visual tests: every browser request to /api/ is answered here with
// synthetic data, so screenshots never depend on a running API or on real (tenant) data.
// A request without a declared response is answered with 404 and recorded: the test fails.
import type { Page } from '@playwright/test';
import { expect } from '@playwright/test';
import type { SystemInfo } from '../../../src/app/core/api/api-types';

export interface SimulatedResponse {
  status?: number;
  body: unknown;
}

/** `METHOD /path` (path after /api/v1) → response. */
export type SimulatedRoutes = Record<string, SimulatedResponse>;

/** Synthetic data: fictitious values only, never copied from a real clinic. */
export const SYNTHETIC = {
  systemInfoUp: {
    name: 'Medical Kuador',
    version: '0.0.0-visual',
    status: 'UP',
    database: 'UP',
  } satisfies SystemInfo,
  systemInfoDegraded: {
    name: 'Medical Kuador',
    version: '0.0.0-visual',
    status: 'UP',
    database: 'DOWN',
  } satisfies SystemInfo,
};

export interface SimulatedApi {
  /** Fails the test if the page called an endpoint the test did not declare. */
  expectNoUnexpectedCalls(): void;
}

export async function simulateApi(page: Page, routes: SimulatedRoutes = {}): Promise<SimulatedApi> {
  const unexpected: string[] = [];

  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname.replace(/^\/api\/v1/, '');
    const key = `${request.method()} ${path}`;
    const response = routes[key];
    if (!response) {
      unexpected.push(key);
      await route.fulfill({
        status: 404,
        contentType: 'application/problem+json',
        body: JSON.stringify({ status: 404, code: 'NOT_FOUND', title: 'Not simulated' }),
      });
      return;
    }
    await route.fulfill({
      status: response.status ?? 200,
      contentType: 'application/json',
      body: JSON.stringify(response.body),
    });
  });

  return {
    expectNoUnexpectedCalls: () => expect(unexpected, 'API calls without a simulated response').toEqual([]),
  };
}
