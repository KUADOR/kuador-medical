// MEDK-44 · Loose styles of the secondary screens (MEDK-33 Phase 0 inventory, audit.md § 2.1).
// «Done» for the web migration means this count is zero: no color, length or weight literal,
// no alias of the old variables and no global class of the pages before the migration in the
// files of the secondary screens. Lengths and colors live in the tokens and in the design
// system library (src/app/design-system/), which these files only use.
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

const webRoot = fileURLToPath(new URL('../../', import.meta.url));
const features = join(webRoot, 'src/app/features');

/**
 * Secondary screens of the inventory (audit.md § 1.1, type V). The main screens (login and its
 * selection, patient search, patient record and the app frame) are redesigned in MEDK-42 and
 * MEDK-43, as is the guardian link list shared with the patient record.
 */
const SECONDARY_SCREENS = [
  'auth/accept-invitation',
  'auth/forgot-password',
  'auth/reset-password',
  'auth/self-registration',
  'auth/verify-email',
  'change-password',
  'message',
  'patients/my-guardian-links',
  'patients/patient-form',
  'patients/self-registration-settings',
  'patients/shared/guardian-person-fields.ts',
  'platform',
  'roles',
  'signup',
  'staff-users',
  'status',
];

/** Same list as src/testing/migrated-screen.ts (global classes of styles.scss before MEDK-44). */
const LEGACY_CLASSES = [
  'page', 'page--wide', 'page--narrow', 'page__header', 'hint', 'form', 'field', 'field__hint',
  'field__error', 'checkbox', 'checkbox__code', 'actions', 'alert', 'alert--success',
  'alert--info', 'button', 'button--secondary', 'button--danger', 'badge', 'card-list', 'card',
  'card__title', 'tag-list', 'table-wrapper', 'table', 'radio-group', 'details', 'filters',
  'pagination', 'choice-list', 'choice-list__button', 'fieldset', 'search', 'search__row',
  'choice-row', 'data-list', 'switch', 'switch__track',
];

interface Finding {
  file: string;
  line: number;
  kind: string;
  text: string;
}

const STYLE_PATTERNS: [string, RegExp][] = [
  ['hex color', /#[0-9a-fA-F]{3,8}\b/],
  ['color function', /\b(rgba?|hsla?|hwb|lab|lch|oklab|oklch)\(/],
  ['length literal', /(?<![\w-])\d*\.?\d+(px|rem|em)\b/],
  ['numeric font weight', /font-weight:\s*\d/],
  ['old variable alias', /var\(--(color|space)-|var\(--radius\)/],
];

function filesOf(path: string): string[] {
  const full = join(features, path);
  if (statSync(full).isFile()) {
    return [full];
  }
  return readdirSync(full, { recursive: true, encoding: 'utf8' })
    .map((name) => join(full, name))
    .filter((file) => /\.(html|ts|scss|css)$/.test(file) && !file.endsWith('.spec.ts'));
}

function classTokens(line: string): string[] {
  const tokens: string[] = [];
  for (const match of line.matchAll(/\bclass="([^"]*)"/g)) {
    tokens.push(...match[1].split(/\s+/));
  }
  for (const match of line.matchAll(/\[class\.([\w-]+)\]/g)) {
    tokens.push(match[1]);
  }
  return tokens.filter(Boolean);
}

function looseStyles(): Finding[] {
  const findings: Finding[] = [];
  for (const file of SECONDARY_SCREENS.flatMap(filesOf)) {
    const isStyle = /\.(s?css)$/.test(file);
    readFileSync(file, 'utf8')
      .split('\n')
      .forEach((text, index) => {
        const at = { file: relative(webRoot, file), line: index + 1, text: text.trim() };
        if (isStyle) {
          if (/^\s*(\/\/|\/?\*)/.test(text)) {
            return;
          }
          for (const [kind, pattern] of STYLE_PATTERNS) {
            if (pattern.test(text)) {
              findings.push({ ...at, kind });
            }
          }
          return;
        }
        // Templates and code: inline styles and the global classes of the old pages.
        if (/\bstyle="|\[style[.\]]|\[ngStyle\]/.test(text)) {
          findings.push({ ...at, kind: 'inline style' });
        }
        for (const name of classTokens(text).filter((c) => LEGACY_CLASSES.includes(c))) {
          findings.push({ ...at, kind: `legacy class .${name}` });
        }
      });
  }
  return findings;
}

describe('loose styles of the secondary screens (MEDK-44)', () => {
  it('counts zero in the web inventory', () => {
    const report = looseStyles().map((f) => `${f.file}:${f.line} ${f.kind} · ${f.text}`);

    expect(report).toEqual([]);
  });
});
