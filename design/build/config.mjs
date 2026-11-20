import { fileURLToPath } from 'node:url';
import StyleDictionary from 'style-dictionary';
import { modePreprocessor } from './modes.mjs';

export const DESIGN_DIR = fileURLToPath(new URL('..', import.meta.url));
export const TOKENS_DIR = fileURLToPath(new URL('../tokens', import.meta.url));
export const CONTRAST_PAIRS_FILE = fileURLToPath(
  new URL('../tokens/contrast-pairs.json', import.meta.url),
);

/** Generated files, relative to the repository root. Both are versioned. */
export const OUTPUTS = {
  css: 'web/src/styles/tokens.css',
  dart: 'movil/lib/design_system/tokens.g.dart',
};

/**
 * Style Dictionary instance for one mode. Style Dictionary parses the DTCG sources, applies the
 * mode, resolves references and names the tokens per platform; the formats in build/formats
 * render both modes into a single file per platform.
 * @param {'light' | 'dark'} mode
 * @param {{ tokensDir?: string }} [options]
 */
export function createStyleDictionary(mode, { tokensDir = TOKENS_DIR } = {}) {
  return new StyleDictionary({
    source: [`${tokensDir}/**/*.tokens.json`],
    usesDtcg: true,
    log: { verbosity: 'silent', warnings: 'error', errors: { brokenReferences: 'throw' } },
    hooks: {
      preprocessors: { 'kuador/mode': modePreprocessor(mode) },
    },
    preprocessors: ['kuador/mode'],
    platforms: {
      css: { prefix: 'kd', transforms: ['name/kebab'] },
      dart: { transforms: ['name/camel'] },
    },
  });
}
