import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { checkContrast, contrastRatio } from '../build/checks/contrast.mjs';
import { generate } from '../build/generate.mjs';
import { tokensFixture } from './helpers.mjs';

describe('contrast ratio', () => {
  it('follows the WCAG formula', () => {
    assert.equal(contrastRatio('#000000', '#ffffff'), 21);
    assert.equal(contrastRatio('#ffffff', '#000000'), 21);
    assert.equal(contrastRatio('#3d4a5c', '#3d4a5c'), 1);
    assert.equal(contrastRatio('#767676', '#ffffff').toFixed(2), '4.54');
  });
});

describe('contrast check', () => {
  const tokens = (fg, bg) => [
    { path: ['fg'], $type: 'color', $value: fg },
    { path: ['bg'], $type: 'color', $value: bg },
  ];

  it('requires 4.5:1 for text and 3:1 for non-text', () => {
    // #949494 on white is about 3.03:1: enough for a focus ring, not for text.
    const byMode = { light: tokens('#949494', '#ffffff') };
    const text = checkContrast([{ foreground: 'fg', background: 'bg', kind: 'text' }], byMode);
    const nonText = checkContrast(
      [{ foreground: 'fg', background: 'bg', kind: 'non-text' }],
      byMode,
    );
    assert.equal(text.failures.length, 1);
    assert.equal(nonText.failures.length, 0);
  });

  it('rejects unknown tokens, translucent colors and unknown kinds', () => {
    const pair = (overrides) => [
      { foreground: 'fg', background: 'bg', kind: 'text', ...overrides },
    ];
    assert.throws(
      () => checkContrast(pair({ foreground: 'nope' }), { light: tokens('#000', '#fff') }),
      /token "nope" does not exist/,
    );
    assert.throws(
      () => checkContrast(pair(), { light: tokens('#00000080', '#fff') }),
      /must be opaque/,
    );
    assert.throws(
      () => checkContrast(pair({ kind: 'large' }), { light: tokens('#000', '#fff') }),
      /unknown kind/,
    );
  });

  it('passes every declared pair of the real tokens in light and dark', async () => {
    const { contrast } = await generate();
    assert.ok(contrast.length > 0);
    assert.deepEqual(new Set(contrast.map((r) => r.mode)), new Set(['light', 'dark']));
    assert.deepEqual(
      contrast.filter((r) => !r.passes),
      [],
    );
  });
});

describe('build with a pair below AA', () => {
  it('fails when a declared text pair does not reach 4.5:1 in light', async (t) => {
    const fixture = await tokensFixture((files) => {
      files['color.tokens.json'].text.muted.$value = '#9aa0a6';
    });
    t.after(fixture.cleanup);
    await assert.rejects(
      generate(fixture),
      /Contrast pairs below WCAG AA:\n.*\[light\] text\.muted on surface\.base: \d+\.\d\d:1 < 4\.5:1 \(text\)/,
    );
  });

  it('fails when the pair only breaks in dark mode', async (t) => {
    const fixture = await tokensFixture((files) => {
      files['color.tokens.json'].color['on-accent'].$extensions['com.kuador.modes'].dark =
        '#8a929c';
    });
    t.after(fixture.cleanup);
    await assert.rejects(generate(fixture), /\[dark\] color\.on-accent on color\.accent/);
  });

  it('fails when a newly declared pair does not meet AA', async (t) => {
    const fixture = await tokensFixture((files) => {
      files['contrast-pairs.json'].pairs.push({
        foreground: 'surface.sunken',
        background: 'surface.raised',
        kind: 'text',
      });
    });
    t.after(fixture.cleanup);
    await assert.rejects(generate(fixture), /surface\.sunken on surface\.raised/);
  });
});
