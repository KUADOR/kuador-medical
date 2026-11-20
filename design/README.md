# design · Design tokens

Single source of truth for the styles of `web` (Angular) and `movil` (Flutter) — ADR-006.
Tokens follow the [W3C Design Tokens (DTCG)](https://www.designtokens.org/) format and are
generated with [Style Dictionary](https://styledictionary.com/) into:

| Output | For | Content |
|---|---|---|
| `web/src/styles/tokens.css` | web | CSS custom properties `--kd-*`, light and dark |
| `movil/lib/design_system/tokens.g.dart` | movil | `KdTokens` (`ThemeExtension`), `KdConstants`, `KdGeneratedTheme.light` / `.dark` |

Both outputs are **generated and versioned** (Flutter builds without Node). Never edit them
by hand: change `design/tokens/` and run the build.

The values are the **final ones of the identity guide** (MEDK-39), taken from the reference
image `design/reference/medical-inspirations.avif`. The guide, its named rules and a summary of
the tokens are in [`DESIGN.md`](../DESIGN.md) at the repository root; a test fails if that summary
drifts from `design/tokens/`.

| Folder | Content |
|---|---|
| `tokens/` | DTCG source of every style value. |
| `icons/` | The SVG icon set (Lucide line icons, ISC license): 24px grid, 2px stroke, `currentColor`. |
| `reference/` | Reference image (and mockups, when they arrive). |

## Commands

Requires Node.js `^22.22.3` or `^24.15.0`.

```bash
cd design
npm ci
npm run build   # checks the contract and contrast, then writes both outputs
npm run check   # same checks; fails if the versioned outputs are out of date (CI)
npm test        # generator tests: outputs, contract, modes, contrast, icons and DESIGN.md
```

## Contract

The contract is the list of roles in `build/contract.mjs`: name, DTCG type and whether the role
changes between modes. Web and Flutter depend only on these names. The build fails if a role is
missing, changes type, or a modal role has no dark value. Renaming or removing a role is a
breaking change for both platforms.

| Group | Roles | Type |
|---|---|---|
| `color.*` | `accent`, `on-accent`, `focus`, `ok`, `error`, `on-error`, `warning`, `info` | color (modal) |
| `surface.*` | `base`, `sunken`, `raised`, `overlay` (layers, lowest to highest) | color (modal) |
| `text.*` | `default`, `muted` | color (modal) |
| `border.*` | `hairline` (decorative divider), `strong` (form controls) | border (modal) |
| `shadow.*` | `sm`, `md`, `lg` | shadow (modal) |
| `radius.*` | `sm`, `md`, `lg`, `full` | dimension |
| `space.*` | `0`, `1`, `2`, `3`, `4`, `6`, `8`, `12` (multiples of 4px) | dimension |
| `size.*` | `target-min` (44px touch target) | dimension |
| `motion.*` | `duration.fast/normal/slow`, `easing.standard/enter/exit` | duration, cubicBezier |
| `type.*` | `family.base/mono`, `weight.*`, `size.*`, `line-height.*`, and the styles `display`, `title`, `subtitle`, `body`, `body-sm`, `label`, `caption` | fontFamily, fontWeight, dimension, number, typography |

Names per platform: `color.on-accent` → CSS `--kd-color-on-accent`, Dart `colorOnAccent`.

## Modes

`$value` is the light value. A modal token declares its dark value in
`$extensions["com.kuador.modes"].dark`. The build resolves each mode separately, so references
take the value of the mode being built.

- **CSS:** light in `:root`; dark under `@media (prefers-color-scheme: dark)`. Tests can force a
  mode with `data-theme="light"` or `data-theme="dark"` on the root element. Only modal tokens are
  repeated in the dark blocks. Spacing and type sizes are in `rem`; radii, borders and shadows in `px`.
- **Dart:** `KdTokens.light` / `KdTokens.dark`, registered in `KdGeneratedTheme.light` / `.dark`;
  use them with `ThemeMode.system` and read them with `KdTokens.of(context)`. Tokens equal in both
  modes are also in `KdConstants` for `const` widgets.

Flutter cannot read a CSS font stack: a `fontFamily` token may set
`$extensions["com.kuador.dart"].fontFamily` (`null` = platform font); otherwise Dart uses the
first name of the list.

## Contrast

`tokens/contrast-pairs.json` lists the pairs checked in **every mode**: `text` needs 4.5:1
(WCAG 2.2 AA, 1.4.3) and `non-text` 3:1 (1.4.11). The build fails and names the pair, the mode
and the ratio when one does not meet its minimum. Colors in a pair must be opaque. When a new
foreground/background combination is used, add it here.
