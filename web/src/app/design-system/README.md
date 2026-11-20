# Design system · web

Component library of the identity guide (`DESIGN.md`, epic MEDK-33, task MEDK-40). Standalone
Angular components whose styles read **only** the `--kd-*` tokens of `src/styles/tokens.css`.
Pages import from `index.ts` only.

| Component | Selector | Notes |
|---|---|---|
| `KdButton` | `button[kdButton]`, `a[kdButton]` | `variant`: `primary` (the one filled accent action of a view, R-02; never the default), `secondary` (default, outlined), `tertiary` (text), `danger`. Optional decorative `icon`. |
| `KdIconButton` | `button[kdIconButton]` | Required `icon` and `label` (accessible name); 44 × 44 px. |
| `KdIcon` | `kd-icon` | Lucide set from `design/icons` (generated, `npm run icons:generate`). Decorative unless it has a `label`. `size` 20 or 24. |
| `KdField` + `KdInput` | `kd-field` + `input/select/textarea[kdInput]` | Label, hint and error wired with `for`, `aria-describedby`, `aria-invalid`, `aria-required`. Shows the Spanish message of the reactive control once touched (`markAllAsTouched()` on submit), or the page's `error` (e.g. from the API). Checkboxes get the label after the control. |
| `KdErrorSummary` | `kd-error-summary` | Form error summary (`role="alert"`); items with `fieldId` link to and focus the control. |
| `KdShell` | `kd-shell` | Skip link, top bar, side navigation from 64rem, menu button below (Escape and link clicks close it). Slots `[kdShellNav]`, `[kdShellAccount]`, content. |
| `KdNav` | `kd-nav` | Router links with `aria-current="page"`. Items come from the page, already filtered by permission and plan (`menuFor`). |
| `KdList` / `KdListItem` | `kd-list` / `kd-list-item` | Rows with icon, heading, description and meta; `link` makes the row one link; `selected` = soft accent (R-10) + `aria-current`; end actions in `[kdListItemEnd]`. |
| `KdCard` | `kd-card` | Named region with heading (`headingLevel` 2–4), description, body and `[kdCardActions]`. No shadow (R-04). |
| `KdDialog` | `kd-dialog` | Native modal `<dialog>`: `[(open)]`, `closed`, `[kdDialogActions]`. |
| `KdAlert` | `kd-alert` | `tone` info, ok, warning, error: icon + text (R-08); error is `role="alert"`, the rest `role="status"`; optional `dismissible`. |
| `KdEmptyState`, `KdLoadingState`, `KdErrorState` | `kd-empty-state`, `kd-loading-state`, `kd-error-state` | Empty (with action slot), loading (polite status) and error (alert with retry). |

## Tests

- Unit (`*.spec.ts`, `npm test`): behavior, ARIA wiring and axe-core (WCAG A/AA) per component.
- Visual (`e2e/visual/design-system.visual.ts`, `npm run test:visual`): the catalog route
  `/design-system` and `/design-system/shell` (development builds only, synthetic data) in light,
  dark and mobile: screenshots, axe with color contrast, keyboard focus ring on every control and
  44 × 44 px targets. Baselines are generated with `npm run test:visual:docker`.
- Style guards (`npm run lint`): pixel values are allowed here only; colors always come from tokens.
