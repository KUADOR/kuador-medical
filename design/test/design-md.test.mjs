import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { describe, it } from 'node:test';
import { DESIGN_DIR, TOKENS_DIR } from '../build/config.mjs';
import { MODES_EXTENSION } from '../build/contract.mjs';

// DESIGN.md repeats the main tokens in its header for people. design/tokens is the source of
// truth: this test fails when the header drifts from it.
const REPO_ROOT = resolve(DESIGN_DIR, '..');
const designMd = await readFile(join(REPO_ROOT, 'DESIGN.md'), 'utf8');
const header = designMd.match(/^---\n([\s\S]*?)\n---\n/)?.[1] ?? '';
const readTokens = async (name) => JSON.parse(await readFile(join(TOKENS_DIR, name), 'utf8'));
const colors = await readTokens('color.tokens.json');
const borders = await readTokens('border.tokens.json');
const radii = await readTokens('radius.tokens.json');
const typography = await readTokens('typography.tokens.json');

/** Lines of one top-level group of the header, e.g. "color:" up to the next group. */
function group(name) {
  const lines = header.split('\n');
  const start = lines.indexOf(`${name}:`);
  assert.notEqual(start, -1, `DESIGN.md header has no "${name}:" group`);
  const end = lines.findIndex((line, i) => i > start && /^\S/.test(line));
  return lines.slice(start + 1, end === -1 ? undefined : end).join('\n');
}

function expectModes(groupName, role, light, dark) {
  assert.match(
    group(groupName),
    new RegExp(`^  ${role}: \\{ light: '${light}', dark: '${dark}' \\}$`, 'm'),
    `${groupName}.${role} must be { light: '${light}', dark: '${dark}' }`,
  );
}

describe('DESIGN.md', () => {
  it('has a token header and links the reference image', async () => {
    assert.ok(header.length > 0, 'YAML header between --- lines');
    assert.match(header, /^reference: design\/reference\/medical-inspirations\.avif$/m);
    assert.match(designMd, /\]\(design\/reference\/medical-inspirations\.avif\)/);
    await readFile(join(DESIGN_DIR, 'reference', 'medical-inspirations.avif'));
  });

  for (const groupName of ['color', 'surface', 'text']) {
    for (const [role, token] of Object.entries(colors[groupName])) {
      if (role.startsWith('$')) continue;
      it(`repeats ${groupName}.${role} in light and dark`, () => {
        expectModes(groupName, role, token.$value, token.$extensions[MODES_EXTENSION].dark);
      });
    }
  }

  for (const [role, token] of Object.entries(borders.border)) {
    if (role.startsWith('$')) continue;
    it(`repeats the color of border.${role} in light and dark`, () => {
      expectModes(
        'border',
        role,
        token.$value.color,
        token.$extensions[MODES_EXTENSION].dark.color,
      );
    });
  }

  it('repeats the radii and the base font family', () => {
    for (const [role, token] of Object.entries(radii.radius)) {
      if (role.startsWith('$')) continue;
      assert.match(header, new RegExp(`radius: \\{.*\\b${role}: ${token.$value}\\b`), role);
    }
    const family = typography.type.family.base.$value[0];
    assert.match(header, new RegExp(`family: \\{ base: ${family},`));
  });

  it('names its rules', () => {
    for (const rule of [
      'Un solo acento',
      'Una acción primaria por vista',
      'Sombra solo en lo que flota',
      'Objetivo de 44 px',
    ]) {
      assert.match(designMd, new RegExp(`\\*\\*R-\\d{2} ${rule}\\*\\*`), rule);
    }
  });
});
