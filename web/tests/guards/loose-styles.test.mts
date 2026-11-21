// MEDK-44 · Loose styles of the secondary screens (MEDK-33 Phase 0 inventory, audit.md § 2.1).
// «Done» for the web migration means this count is zero: no color, length or weight literal,
// no alias of the old variables and no global class of the pages before the migration in the
// files of the secondary screens. Lengths and colors live in the tokens and in the design
// system library (src/app/design-system/), which these files only use.
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';
import { LEGACY_CLASSES } from '../../src/testing/legacy-classes';

const webRoot = fileURLToPath(new URL('../../', import.meta.url));
const features = join(webRoot, 'src/app/features');

/**
 * Secondary screens of the inventory (audit.md § 1.1, type V), with every component they render.
 * The main screens (login and its selection, patient search, patient record and the app frame)
 * were redesigned in MEDK-42 and MEDK-43; the last test below covers the whole app.
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
  // Shared by my authorized contacts (secondary) and the patient record (main, MEDK-43).
  'patients/shared',
  'platform',
  'roles',
  'signup',
  'staff-users',
  'status',
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

function filesOf(path: string, root = features): string[] {
  const full = join(root, path);
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

const legacyClasses: readonly string[] = LEGACY_CLASSES;

/**
 * Findings in `files`. Stylesheets: literals and old aliases (only with `checkLiterals`).
 * Templates and code: inline styles and the global classes of the old pages.
 */
function looseStyles(files: string[], checkLiterals = true): Finding[] {
  const findings: Finding[] = [];
  for (const file of files) {
    const isStyle = /\.(s?css)$/.test(file);
    readFileSync(file, 'utf8')
      .split('\n')
      .forEach((text, index) => {
        const at = { file: relative(webRoot, file), line: index + 1, text: text.trim() };
        if (isStyle) {
          if (!checkLiterals || /^\s*(\/\/|\/?\*)/.test(text)) {
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
        for (const name of classTokens(text).filter((c) => legacyClasses.includes(c))) {
          findings.push({ ...at, kind: `legacy class .${name}` });
        }
      });
  }
  return findings;
}

const report = (findings: Finding[]) =>
  findings.map((f) => `${f.file}:${f.line} ${f.kind} · ${f.text}`);

describe('loose styles of the secondary screens (MEDK-44)', () => {
  it('counts zero in the web inventory', () => {
    const files = SECONDARY_SCREENS.flatMap((path) => filesOf(path));

    expect(report(looseStyles(files))).toEqual([]);
  });

  it('finds no global class of the old pages and no inline style anywhere in the app', () => {
    const files = filesOf('.', join(webRoot, 'src/app'));

    expect(report(looseStyles(files, false))).toEqual([]);
  });

  it('leaves no old global class nor variable alias in the global stylesheet', () => {
    const css = readFileSync(join(webRoot, 'src/styles.scss'), 'utf8');
    const rules = legacyClasses.filter((name) =>
      new RegExp(`\\.${name.replace(/-/g, '\\-')}(?![\\w-])`).test(css),
    );

    expect(rules, 'old global classes in styles.scss').toEqual([]);
    expect(css).not.toMatch(/--(color|space)-[\w-]+\s*:|--radius\s*:/);
  });
});
