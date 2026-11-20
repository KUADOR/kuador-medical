// The token contract shared by web and movil: role names, DTCG types and modes.
// Every role listed here must exist in design/tokens with a light value; roles marked
// `modal` must also declare a dark value. Renaming or removing a role is a breaking change
// for both platforms.

export const MODES = ['light', 'dark'];

/** Extension key that holds per-mode values: $extensions["com.kuador.modes"].dark */
export const MODES_EXTENSION = 'com.kuador.modes';

/** Extension key with Dart-only hints: $extensions["com.kuador.dart"].fontFamily */
export const DART_EXTENSION = 'com.kuador.dart';

/** @type {Array<{ path: string, type: string, modal?: boolean }>} */
export const CONTRACT = [
  { path: 'color.accent', type: 'color', modal: true },
  { path: 'color.on-accent', type: 'color', modal: true },
  { path: 'color.focus', type: 'color', modal: true },
  { path: 'color.ok', type: 'color', modal: true },
  { path: 'color.error', type: 'color', modal: true },
  { path: 'color.on-error', type: 'color', modal: true },
  { path: 'color.warning', type: 'color', modal: true },
  { path: 'color.info', type: 'color', modal: true },
  { path: 'surface.base', type: 'color', modal: true },
  { path: 'surface.sunken', type: 'color', modal: true },
  { path: 'surface.raised', type: 'color', modal: true },
  { path: 'surface.overlay', type: 'color', modal: true },
  { path: 'text.default', type: 'color', modal: true },
  { path: 'text.muted', type: 'color', modal: true },
  { path: 'border.hairline', type: 'border', modal: true },
  { path: 'border.strong', type: 'border', modal: true },
  { path: 'shadow.sm', type: 'shadow', modal: true },
  { path: 'shadow.md', type: 'shadow', modal: true },
  { path: 'shadow.lg', type: 'shadow', modal: true },
  { path: 'radius.sm', type: 'dimension' },
  { path: 'radius.md', type: 'dimension' },
  { path: 'radius.lg', type: 'dimension' },
  { path: 'radius.full', type: 'dimension' },
  { path: 'space.0', type: 'dimension' },
  { path: 'space.1', type: 'dimension' },
  { path: 'space.2', type: 'dimension' },
  { path: 'space.3', type: 'dimension' },
  { path: 'space.4', type: 'dimension' },
  { path: 'space.6', type: 'dimension' },
  { path: 'space.8', type: 'dimension' },
  { path: 'space.12', type: 'dimension' },
  { path: 'size.target-min', type: 'dimension' },
  { path: 'motion.duration.fast', type: 'duration' },
  { path: 'motion.duration.normal', type: 'duration' },
  { path: 'motion.duration.slow', type: 'duration' },
  { path: 'motion.easing.standard', type: 'cubicBezier' },
  { path: 'motion.easing.enter', type: 'cubicBezier' },
  { path: 'motion.easing.exit', type: 'cubicBezier' },
  { path: 'type.family.base', type: 'fontFamily' },
  { path: 'type.family.mono', type: 'fontFamily' },
  { path: 'type.display', type: 'typography' },
  { path: 'type.title', type: 'typography' },
  { path: 'type.subtitle', type: 'typography' },
  { path: 'type.body', type: 'typography' },
  { path: 'type.body-sm', type: 'typography' },
  { path: 'type.label', type: 'typography' },
  { path: 'type.caption', type: 'typography' },
];

/**
 * Checks the contract against the resolved tokens of each mode.
 * @param {Record<string, Array<import('style-dictionary/types').TransformedToken>>} tokensByMode
 * @returns {string[]} human readable violations (empty when the contract holds)
 */
export function checkContract(tokensByMode) {
  const violations = [];
  const light = indexByPath(tokensByMode.light);
  for (const role of CONTRACT) {
    const token = light.get(role.path);
    if (!token) {
      violations.push(`missing role "${role.path}" (${role.type})`);
      continue;
    }
    if (token.$type !== role.type) {
      violations.push(`role "${role.path}" must be of type ${role.type}, found ${token.$type}`);
    }
    if (role.modal && token.original?.$extensions?.[MODES_EXTENSION]?.dark === undefined) {
      violations.push(`role "${role.path}" must declare a dark value`);
    }
  }
  return violations;
}

/** @param {Array<{ path: string[] }>} tokens */
export function indexByPath(tokens) {
  return new Map(tokens.map((token) => [token.path.join('.'), token]));
}
