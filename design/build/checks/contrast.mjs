import { indexByPath } from '../contract.mjs';
import { parseColor } from '../values.mjs';

/** WCAG 2.2 minimum ratios: 1.4.3 (text, AA) and 1.4.11 (non-text). */
export const MIN_RATIO = { text: 4.5, 'non-text': 3 };

/** Relative luminance (WCAG 2.2). */
export function relativeLuminance(color) {
  const { r, g, b } = parseColor(color);
  const linear = (channel) => {
    const c = channel / 255;
    return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b);
}

/** Contrast ratio between two opaque colors, from 1 to 21. */
export function contrastRatio(foreground, background) {
  const [high, low] = [relativeLuminance(foreground), relativeLuminance(background)].sort(
    (x, y) => y - x,
  );
  return (high + 0.05) / (low + 0.05);
}

/**
 * Checks every declared pair in every mode.
 * @param {Array<{ foreground: string, background: string, kind: 'text' | 'non-text' }>} pairs
 * @param {Record<string, Array<{ path: string[], $type: string, $value: unknown }>>} tokensByMode
 * @returns {{ results: Array<object>, failures: Array<object> }}
 */
export function checkContrast(pairs, tokensByMode) {
  const results = [];
  for (const [mode, tokens] of Object.entries(tokensByMode)) {
    const index = indexByPath(tokens);
    for (const pair of pairs) {
      const minimum = MIN_RATIO[pair.kind];
      if (minimum === undefined) {
        throw new Error(`Contrast pair ${describe(pair)}: unknown kind "${pair.kind}"`);
      }
      const foreground = colorOf(index, pair.foreground, pair);
      const background = colorOf(index, pair.background, pair);
      const ratio = contrastRatio(foreground, background);
      results.push({ ...pair, mode, minimum, ratio, passes: ratio >= minimum });
    }
  }
  return { results, failures: results.filter((result) => !result.passes) };
}

/** One line per failing pair, for build errors. */
export function describeFailures(failures) {
  return failures
    .map(
      (f) => `  [${f.mode}] ${describe(f)}: ${f.ratio.toFixed(2)}:1 < ${f.minimum}:1 (${f.kind})`,
    )
    .join('\n');
}

function colorOf(index, path, pair) {
  const token = index.get(path);
  if (!token) {
    throw new Error(`Contrast pair ${describe(pair)}: token "${path}" does not exist`);
  }
  let value;
  if (token.$type === 'color') {
    value = token.$value;
  } else if (token.$type === 'border') {
    value = token.$value.color;
  } else {
    throw new Error(`Contrast pair ${describe(pair)}: "${path}" is not a color or a border`);
  }
  if (parseColor(value).a < 1) {
    throw new Error(`Contrast pair ${describe(pair)}: "${path}" must be opaque to be checked`);
  }
  return value;
}

function describe(pair) {
  return `${pair.foreground} on ${pair.background}`;
}
