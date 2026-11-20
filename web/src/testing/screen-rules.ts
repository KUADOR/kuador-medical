// Rules of the redesigned staff screens (MEDK-33 plan, acceptance criterion 5; DESIGN.md R-02):
// built only with design system components and at most one primary action per view.

/** Global classes of the pages before the redesign; redesigned screens must not use them. */
const LEGACY_CLASSES = [
  'page',
  'page__header',
  'button',
  'button--secondary',
  'field',
  'field__error',
  'field__hint',
  'alert',
  'alert--success',
  'hint',
  'actions',
  'form',
  'badge',
  'table',
  'table-wrapper',
  'search',
  'search__row',
  'choice-list',
  'pagination',
];

/** Primary (filled accent) actions rendered in `root`. */
export function primaryActions(root: Element): HTMLElement[] {
  return Array.from(root.querySelectorAll<HTMLElement>('.kd-button--primary'));
}

/** Fails if the screen uses legacy global classes, raw controls or more than one primary action. */
export function expectDesignSystemScreen(root: Element): void {
  const legacy = LEGACY_CLASSES.filter((name) => root.querySelector(`.${name}`) !== null);
  expect(legacy, 'legacy global classes on a redesigned screen').toEqual([]);

  const rawButtons = Array.from(root.querySelectorAll('button')).filter(
    (button) => !button.matches('.kd-button, .kd-icon-button'),
  );
  expect(
    rawButtons.map((b) => b.textContent?.trim()),
    'buttons outside the library',
  ).toEqual([]);

  const rawControls = Array.from(root.querySelectorAll('input, select, textarea')).filter(
    (control) => !control.matches('.kd-input'),
  );
  expect(
    rawControls.map((c) => c.id || c.getAttribute('name')),
    'raw form controls',
  ).toEqual([]);

  expect(primaryActions(root).length, 'primary actions in the view (R-02)').toBeLessThanOrEqual(1);
}
