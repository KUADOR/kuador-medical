// Rules of the secondary screens migrated in MEDK-44 (MEDK-33 plan, acceptance criterion 8;
// DESIGN.md R-02 and R-06): only the look changes, so the screen must be built with the design
// system components and must not use the global classes of the pages before the migration.

/** Global classes of src/styles.scss before the migration (MEDK-33 Phase 0 inventory). */
export const LEGACY_CLASSES = [
  'page',
  'page--wide',
  'page--narrow',
  'page__header',
  'hint',
  'form',
  'field',
  'field__hint',
  'field__error',
  'checkbox',
  'checkbox__code',
  'actions',
  'alert',
  'alert--success',
  'alert--info',
  'button',
  'button--secondary',
  'button--danger',
  'badge',
  'card-list',
  'card',
  'card__title',
  'tag-list',
  'table-wrapper',
  'table',
  'radio-group',
  'details',
  'filters',
  'pagination',
  'choice-list',
  'choice-list__button',
  'fieldset',
  'search',
  'search__row',
  'choice-row',
  'data-list',
  'switch',
  'switch__track',
] as const;

/**
 * Fails if the screen uses a legacy global class, an inline style, a button or form control
 * outside the library, or more than one primary action (R-02).
 */
export function expectMigratedScreen(root: Element): void {
  const legacy = LEGACY_CLASSES.filter((name) => root.querySelector(`.${name}`) !== null);
  expect(legacy, 'legacy global classes on a migrated screen').toEqual([]);

  const inline = Array.from(root.querySelectorAll('[style]')).map((e) => e.tagName.toLowerCase());
  expect(inline, 'inline styles').toEqual([]);

  const rawButtons = Array.from(root.querySelectorAll('button')).filter(
    (button) => !button.matches('.kd-button, .kd-icon-button, .kd-switch'),
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
    'form controls outside the library',
  ).toEqual([]);

  expect(
    root.querySelectorAll('.kd-button--primary').length,
    'primary actions in the view (R-02)',
  ).toBeLessThanOrEqual(1);
}
