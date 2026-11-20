// Accessibility checks shared by the visual tests: 44 × 44 px targets (R-05) and a visible
// keyboard focus ring (R-09), measured in a real browser.
import { type Locator, type Page, expect } from '@playwright/test';

const TARGET_MIN = 44;
const CONTROLS = 'button, a[href], input, select, textarea';

interface Box {
  name: string;
  width: number;
  height: number;
}

/** Size of the clickable area of every visible control inside `root`. */
export async function targetSizes(root: Locator): Promise<Box[]> {
  return root.evaluate((element, selector) => {
    const describe = (el: Element) =>
      `${el.tagName.toLowerCase()} «${(el.getAttribute('aria-label') ?? el.textContent ?? '').trim().slice(0, 40)}»`;
    return Array.from(element.querySelectorAll<HTMLElement>(selector))
      .filter(
        (el) => el.getClientRects().length > 0 && getComputedStyle(el).visibility !== 'hidden',
      )
      .map((el) => {
        // A checkbox is clicked through its whole label row (kd-field--check).
        const isCheck = el instanceof HTMLInputElement && el.type === 'checkbox';
        const target = isCheck ? (el.closest('kd-field')?.querySelector('label') ?? el) : el;
        const rect = target.getBoundingClientRect();
        return {
          name: describe(el),
          width: Math.round(rect.width),
          height: Math.round(rect.height),
        };
      });
  }, CONTROLS);
}

export function expectTargetsOf44(boxes: Box[]): void {
  expect(boxes.length).toBeGreaterThan(0);
  const small = boxes.filter((b) => b.width < TARGET_MIN || b.height < TARGET_MIN);
  expect(small, 'controls smaller than 44 × 44 px').toEqual([]);
}

/** Tabs through the page and returns the focused controls inside `scope` without a visible ring. */
export async function controlsWithoutFocusRing(
  page: Page,
  scope: string,
  maxTabs = 60,
): Promise<string[]> {
  const missing: string[] = [];
  const seen = new Set<string>();
  await page.locator('body').click({ position: { x: 1, y: 1 } });
  for (let i = 0; i < maxTabs; i++) {
    await page.keyboard.press('Tab');
    const focus = await page.evaluate((scopeSelector) => {
      const el = document.activeElement as HTMLElement | null;
      if (!el || !el.closest(scopeSelector)) {
        return null;
      }
      const style = getComputedStyle(el);
      return {
        key: `${el.tagName}#${el.id}|${el.getAttribute('aria-label') ?? el.textContent?.trim().slice(0, 30)}`,
        visible: style.outlineStyle !== 'none' && parseFloat(style.outlineWidth) >= 2,
      };
    }, scope);
    if (!focus) {
      continue;
    }
    if (seen.has(focus.key)) {
      break;
    }
    seen.add(focus.key);
    if (!focus.visible) {
      missing.push(focus.key);
    }
  }
  expect(seen.size, 'focusable controls reached with Tab').toBeGreaterThan(5);
  return missing;
}
