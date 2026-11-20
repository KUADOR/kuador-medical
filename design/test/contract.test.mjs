import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { generate, resolveTokens } from '../build/generate.mjs';
import { tokensFixture } from './helpers.mjs';

describe('token contract', () => {
  it('fails when a role is missing', async (t) => {
    const fixture = await tokensFixture((files) => {
      delete files['color.tokens.json'].surface.raised;
    });
    t.after(fixture.cleanup);
    await assert.rejects(generate(fixture), /missing role "surface\.raised"/);
  });

  it('fails when a modal role has no dark value', async (t) => {
    const fixture = await tokensFixture((files) => {
      delete files['border.tokens.json'].border.hairline.$extensions;
    });
    t.after(fixture.cleanup);
    await assert.rejects(generate(fixture), /role "border\.hairline" must declare a dark value/);
  });

  it('fails when a role changes type', async (t) => {
    const fixture = await tokensFixture((files) => {
      files['typography.tokens.json'].type.body.$type = 'dimension';
      files['typography.tokens.json'].type.body.$value = '16px';
    });
    t.after(fixture.cleanup);
    await assert.rejects(generate(fixture), /role "type\.body" must be of type typography/);
  });

  it('fails on a broken reference', async (t) => {
    const fixture = await tokensFixture((files) => {
      files['typography.tokens.json'].type.body.$value.fontSize = '{type.size.huge}';
    });
    t.after(fixture.cleanup);
    await assert.rejects(generate(fixture));
  });
});

describe('modes', () => {
  it('resolves references with the value of each mode', async (t) => {
    const fixture = await tokensFixture((files) => {
      files['color.tokens.json'].color['link'] = { $value: '{color.accent}' };
    });
    t.after(fixture.cleanup);
    const tokens = await resolveTokens(fixture);
    const link = (mode) => tokens.css[mode].find((token) => token.name === 'kd-color-link').$value;
    assert.equal(link('light'), '#3d4a5c');
    assert.equal(link('dark'), '#b9c4d2');
  });
});
