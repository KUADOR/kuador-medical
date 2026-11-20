import { readFile } from 'node:fs/promises';
import { checkContrast, describeFailures } from './checks/contrast.mjs';
import { CONTRAST_PAIRS_FILE, OUTPUTS, TOKENS_DIR, createStyleDictionary } from './config.mjs';
import { MODES, checkContract } from './contract.mjs';
import { formatCss } from './formats/css.mjs';
import { formatDart } from './formats/dart-theme.mjs';

/**
 * Resolves the tokens of every mode and platform.
 * @returns {Promise<Record<'css' | 'dart', Record<'light' | 'dark', Array<object>>>>}
 */
export async function resolveTokens({ tokensDir = TOKENS_DIR } = {}) {
  const resolved = { css: {}, dart: {} };
  for (const mode of MODES) {
    const sd = createStyleDictionary(mode, { tokensDir });
    for (const platform of Object.keys(resolved)) {
      const dictionary = await sd.getPlatformTokens(platform);
      resolved[platform][mode] = dictionary.allTokens;
    }
  }
  return resolved;
}

/**
 * Generates the CSS and Dart outputs in memory. Fails when the contract does not hold or a
 * declared contrast pair does not meet WCAG AA in any mode.
 * @returns {Promise<{ files: Record<string, string>, contrast: Array<object> }>}
 */
export async function generate({ tokensDir = TOKENS_DIR, pairsFile = CONTRAST_PAIRS_FILE } = {}) {
  const tokens = await resolveTokens({ tokensDir });

  const violations = checkContract(tokens.css);
  if (violations.length > 0) {
    throw new Error(`The token contract does not hold:\n  ${violations.join('\n  ')}`);
  }

  const { pairs } = JSON.parse(await readFile(pairsFile, 'utf8'));
  const { results, failures } = checkContrast(pairs, tokens.css);
  if (failures.length > 0) {
    throw new Error(`Contrast pairs below WCAG AA:\n${describeFailures(failures)}`);
  }

  return {
    files: {
      [OUTPUTS.css]: formatCss(tokens.css),
      [OUTPUTS.dart]: formatDart(tokens.dart),
    },
    contrast: results,
  };
}
