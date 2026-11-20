import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { describe, it } from 'node:test';
import { DESIGN_DIR, OUTPUTS } from '../build/config.mjs';
import { CONTRACT } from '../build/contract.mjs';
import { generate, resolveTokens } from '../build/generate.mjs';
import { tokensFixture } from './helpers.mjs';

const REPO_ROOT = resolve(DESIGN_DIR, '..');
const { files } = await generate();
const tokens = await resolveTokens();
const css = files[OUTPUTS.css];
const dart = files[OUTPUTS.dart];

describe('generated outputs', () => {
  // The versioned files are the snapshot: a token change must regenerate and commit them.
  for (const relativePath of Object.values(OUTPUTS)) {
    it(`${relativePath} matches the versioned snapshot`, async () => {
      const versioned = await readFile(resolve(REPO_ROOT, relativePath), 'utf8');
      assert.equal(files[relativePath], versioned, 'Run "npm run build" in design/ and commit');
    });
  }

  it('is deterministic', async () => {
    const again = await generate();
    assert.deepEqual(again.files, files);
  });

  it('exposes the same tokens, in the same order, on both platforms', () => {
    const paths = (list) => list.map((token) => token.path.join('.'));
    assert.deepEqual(paths(tokens.css.light), paths(tokens.dart.light));
    assert.deepEqual(paths(tokens.css.light), paths(tokens.css.dark));
  });
});

describe('CSS output', () => {
  const [lightBlock, rest] = css.split('@media (prefers-color-scheme: dark)');
  const [mediaBlock, forcedDarkBlock] = rest.split("[data-theme='dark'] {");

  it('declares every token in the light block, with the kd prefix', () => {
    for (const token of tokens.css.light) {
      assert.match(lightBlock, new RegExp(`\\n  --${token.name}: `), token.name);
      assert.ok(token.name.startsWith('kd-'));
    }
  });

  it('follows the OS preference and can be forced with data-theme', () => {
    assert.match(lightBlock, /:root,\n\[data-theme='light'\] \{\n  color-scheme: light;/);
    assert.match(mediaBlock, /:root:not\(\[data-theme='light'\]\) \{\n    color-scheme: dark;/);
    assert.match(forcedDarkBlock, /^\n  color-scheme: dark;/);
  });

  it('overrides every modal role in both dark blocks', () => {
    for (const role of CONTRACT.filter((r) => r.modal)) {
      const name = `kd-${role.path.replaceAll('.', '-')}`;
      assert.match(mediaBlock, new RegExp(`--${name}: `), `media: ${name}`);
      assert.match(forcedDarkBlock, new RegExp(`--${name}: `), `forced: ${name}`);
    }
  });

  it('does not repeat mode-independent tokens in the dark blocks', () => {
    assert.doesNotMatch(mediaBlock, /--kd-space-4:/);
    assert.doesNotMatch(forcedDarkBlock, /--kd-radius-md:/);
  });

  it('uses rem for spacing and type sizes and px for radii and hairlines', () => {
    assert.match(lightBlock, /--kd-space-4: 1rem;/);
    assert.match(lightBlock, /--kd-type-size-md: 1rem;/);
    assert.match(lightBlock, /--kd-size-target-min: 2\.75rem;/);
    assert.match(lightBlock, /--kd-radius-md: 10px;/);
    assert.match(lightBlock, /--kd-border-hairline: 1px solid #[0-9a-f]{6};/);
    assert.match(lightBlock, /--kd-type-body: 400 1rem\/1\.5 Figtree, system-ui, /);
  });
});

describe('Dart output', () => {
  it('defines the ThemeExtension, the constants and both ThemeData', () => {
    assert.match(dart, /class KdTokens extends ThemeExtension<KdTokens>/);
    assert.match(dart, /static const KdTokens light = KdTokens\(/);
    assert.match(dart, /static const KdTokens dark = KdTokens\(/);
    assert.match(dart, /abstract final class KdConstants/);
    assert.match(
      dart,
      /static final ThemeData light = _build\(KdTokens\.light, Brightness\.light\);/,
    );
    assert.match(dart, /static final ThemeData dark = _build\(KdTokens\.dark, Brightness\.dark\);/);
    assert.match(dart, /extensions: <ThemeExtension<dynamic>>\[t\]/);
  });

  it('declares, copies and lerps every token', () => {
    for (const token of tokens.dart.light) {
      assert.match(dart, new RegExp(`\\n  final [\\w<>?]+ ${token.name};`), token.name);
      assert.match(dart, new RegExp(`${token.name}: ${token.name} \\?\\? this\\.${token.name},`));
      assert.match(dart, new RegExp(`\\n      ${token.name}: .*other\\.${token.name}`));
    }
  });

  it('uses the dark values in KdTokens.dark', () => {
    const darkInstance = dart.split('static const KdTokens dark = KdTokens(')[1].split(');')[0];
    const darkAccent = tokens.dart.dark.find((t) => t.name === 'colorAccent').$value;
    const argb = `0xFF${darkAccent.slice(1).toUpperCase()}`;
    assert.match(darkInstance, new RegExp(`colorAccent: Color\\(${argb}\\),`));
  });

  it('keeps mode-independent tokens in KdConstants and modal tokens out of it', () => {
    const constants = dart.split('abstract final class KdConstants {')[1].split('\n}')[0];
    assert.match(constants, /static const double space4 = 16\.0;/);
    assert.match(
      constants,
      /static const Duration motionDurationFast = Duration\(milliseconds: 120\);/,
    );
    assert.doesNotMatch(constants, /colorAccent/);
    assert.doesNotMatch(constants, /surfaceRaised/);
  });

  it('uses the Dart family hint in the constants and the text styles', () => {
    assert.match(dart, /static const String typeFamilyBase = 'Figtree';/);
    assert.match(
      dart,
      /typeBody = TextStyle\(fontFamily: 'Figtree', fontSize: 16\.0, fontWeight: FontWeight\.w400, height: 1\.5\);/,
    );
  });

  it('lets Flutter use the platform font when the family says so', async (t) => {
    const fixture = await tokensFixture((files) => {
      files['typography.tokens.json'].type.family.base.$extensions['com.kuador.dart'].fontFamily =
        null;
    });
    t.after(fixture.cleanup);
    const platformDart = (await generate(fixture)).files[OUTPUTS.dart];
    assert.match(platformDart, /static const String\? typeFamilyBase = null;/);
    assert.match(
      platformDart,
      /typeBody = TextStyle\(fontSize: 16\.0, fontWeight: FontWeight\.w400, height: 1\.5\);/,
    );
  });
});
