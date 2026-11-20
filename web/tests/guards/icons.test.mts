// The icon component of the design system reads a file generated from design/icons
// (scripts/generate-icons.mjs). Like the tokens, a stale or hand-edited copy is drift.
import { readFile } from 'node:fs/promises';
import { describe, expect, it } from 'vitest';
// @ts-expect-error plain ES module script without type declarations
import { OUTPUT, generate } from '../../scripts/generate-icons.mjs';

describe('generated icon set', () => {
  it('matches design/icons (run `npm run icons:generate` after changing an icon)', async () => {
    expect(await readFile(OUTPUT, 'utf8')).toBe(await generate());
  });
});
