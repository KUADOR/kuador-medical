import { MODES_EXTENSION } from './contract.mjs';

/**
 * Style Dictionary preprocessor that swaps every token value for the one declared for `mode`
 * in $extensions["com.kuador.modes"]. Runs before references are resolved, so a token that
 * references a modal token also gets the value of that mode.
 * @param {string} mode
 */
export function modePreprocessor(mode) {
  return (dictionary) => (mode === 'light' ? dictionary : swap(structuredClone(dictionary), mode));
}

function swap(node, mode) {
  if (node === null || typeof node !== 'object' || Array.isArray(node)) {
    return node;
  }
  if ('$value' in node) {
    const override = node.$extensions?.[MODES_EXTENSION]?.[mode];
    if (override !== undefined) {
      node.$value = override;
    }
    return node;
  }
  for (const key of Object.keys(node)) {
    if (!key.startsWith('$')) {
      swap(node[key], mode);
    }
  }
  return node;
}
