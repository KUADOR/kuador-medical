// axe-core in a real browser: unlike jsdom, it can check color contrast in each theme.
import type { Page } from '@playwright/test';
import { expect } from '@playwright/test';

const axePath = require.resolve('axe-core/axe.min.js');

interface BrowserViolation {
  id: string;
  help: string;
  targets: string[];
}

export async function expectNoAxeViolations(page: Page): Promise<void> {
  await page.addScriptTag({ path: axePath });
  const violations = await page.evaluate(async () => {
    const axe = (window as unknown as { axe: typeof import('axe-core') }).axe;
    const results = await axe.run(document, {
      runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'] },
      resultTypes: ['violations'],
    });
    return results.violations.map((v) => ({
      id: v.id,
      help: v.help,
      targets: v.nodes.flatMap((n) => n.target.map(String)),
    }));
  });
  expect(violations as BrowserViolation[], 'axe violations').toEqual([]);
}
