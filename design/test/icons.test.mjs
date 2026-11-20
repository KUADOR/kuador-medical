import assert from 'node:assert/strict';
import { readdir, readFile } from 'node:fs/promises';
import { join } from 'node:path';
import { describe, it } from 'node:test';
import { DESIGN_DIR } from '../build/config.mjs';

// The icon set of the identity guide (DESIGN.md § Iconography): one line style, 24px grid,
// color inherited from the text so a token decides it on each platform.
const ICONS_DIR = join(DESIGN_DIR, 'icons');
const names = (await readdir(ICONS_DIR)).filter((name) => name.endsWith('.svg'));

describe('icon set', () => {
  it('is not empty and ships its license', async () => {
    assert.ok(names.length > 0);
    assert.match(await readFile(join(ICONS_DIR, 'LICENSE'), 'utf8'), /ISC License/);
  });

  for (const name of names) {
    it(`${name} follows the icon rules`, async () => {
      const svg = await readFile(join(ICONS_DIR, name), 'utf8');
      assert.match(name, /^[a-z0-9]+(-[a-z0-9]+)*\.svg$/, 'kebab-case file name');
      assert.match(svg, /viewBox="0 0 24 24"/, '24px grid');
      assert.match(svg, /fill="none"/, 'line icons, no fill');
      assert.match(svg, /stroke="currentColor"/, 'color comes from the text color');
      assert.match(svg, /stroke-width="2"/, 'one stroke width for the whole set');
      assert.doesNotMatch(svg, /#[0-9a-fA-F]{3,8}\b|rgba?\(/, 'no literal colors');
      assert.doesNotMatch(
        svg,
        /<(script|image|foreignObject)\b|\bon\w+=|href="http/i,
        'no active content',
      );
    });
  }
});
