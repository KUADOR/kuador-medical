// axe-core in the unit tests (MEDK-33 plan: web accessibility). Checks the rendered DOM of a
// component or page against WCAG 2.x A and AA rules.
//
// jsdom has no layout or computed colors, so `color-contrast` is off here: contrast is checked
// by the token build (design/build/checks/contrast.mjs) and by axe in the Playwright visual tests,
// which run in a real browser in light and dark.
import axe from 'axe-core';

export interface AxeViolation {
  id: string;
  impact: string | null | undefined;
  help: string;
  targets: string[];
}

const RUN_OPTIONS: axe.RunOptions = {
  runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'] },
  rules: { 'color-contrast': { enabled: false } },
  resultTypes: ['violations'],
};

/** Runs axe on an element (e.g. `fixture.nativeElement`) and returns its violations. */
export async function axeViolations(root: Element): Promise<AxeViolation[]> {
  const results = await axe.run(root, RUN_OPTIONS);
  return results.violations.map((v) => ({
    id: v.id,
    impact: v.impact,
    help: v.help,
    targets: v.nodes.flatMap((n) => n.target.map(String)),
  }));
}

/** Fails the test, listing rule, help and targets, if axe finds any violation. */
export async function expectNoAxeViolations(root: Element): Promise<void> {
  const violations = await axeViolations(root);
  if (violations.length > 0) {
    const report = violations
      .map((v) => `- ${v.id} (${v.impact}): ${v.help} → ${v.targets.join(', ')}`)
      .join('\n');
    throw new Error(`axe found ${violations.length} accessibility violation(s):\n${report}`);
  }
}
