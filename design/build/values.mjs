// Parsing helpers shared by the formats and the checks. Accept both the DTCG string form
// ("16px", "120ms") and the object form ({ value: 16, unit: "px" }).

/** @returns {{ value: number, unit: string }} */
export function parseDimension(raw) {
  if (typeof raw === 'object' && raw !== null) {
    return { value: Number(raw.value), unit: raw.unit };
  }
  if (typeof raw === 'number') {
    return { value: raw, unit: 'px' };
  }
  const match = /^(-?\d*\.?\d+)(px|rem)$/.exec(String(raw).trim());
  if (!match) {
    throw new Error(`Unsupported dimension "${raw}" (use px or rem)`);
  }
  return { value: Number(match[1]), unit: match[2] };
}

/** Dimension in logical pixels (1rem = 16px). */
export function toPx(raw) {
  const { value, unit } = parseDimension(raw);
  return unit === 'rem' ? value * 16 : value;
}

/** Duration in milliseconds. */
export function toMs(raw) {
  if (typeof raw === 'object' && raw !== null) {
    return raw.unit === 's' ? Number(raw.value) * 1000 : Number(raw.value);
  }
  const match = /^(\d*\.?\d+)(ms|s)$/.exec(String(raw).trim());
  if (!match) {
    throw new Error(`Unsupported duration "${raw}" (use ms or s)`);
  }
  return match[2] === 's' ? Number(match[1]) * 1000 : Number(match[1]);
}

/**
 * Parses a hex color (#rgb, #rgba, #rrggbb, #rrggbbaa) or a DTCG srgb color object.
 * @returns {{ r: number, g: number, b: number, a: number }} channels 0–255, alpha 0–1
 */
export function parseColor(raw) {
  if (typeof raw === 'object' && raw !== null && Array.isArray(raw.components)) {
    if (raw.colorSpace !== 'srgb') {
      throw new Error(`Unsupported color space "${raw.colorSpace}" (use srgb)`);
    }
    const [r, g, b] = raw.components.map((c) => Math.round(c * 255));
    return { r, g, b, a: raw.alpha ?? 1 };
  }
  const hex = String(raw).trim().replace(/^#/, '');
  if (!/^([0-9a-f]{3,4}|[0-9a-f]{6}|[0-9a-f]{8})$/i.test(hex)) {
    throw new Error(`Unsupported color "${raw}" (use hex)`);
  }
  const full = hex.length <= 4 ? [...hex].map((c) => c + c).join('') : hex;
  const channel = (i) => parseInt(full.slice(i, i + 2), 16);
  return {
    r: channel(0),
    g: channel(2),
    b: channel(4),
    a: full.length === 8 ? channel(6) / 255 : 1,
  };
}

/** Lowercase #rrggbb or #rrggbbaa. */
export function toHex(raw) {
  const { r, g, b, a } = parseColor(raw);
  const pair = (n) => n.toString(16).padStart(2, '0');
  const alpha = a < 1 ? pair(Math.round(a * 255)) : '';
  return `#${pair(r)}${pair(g)}${pair(b)}${alpha}`;
}

/** @param {unknown} raw a shadow value: one shadow object or an array of them */
export function shadowLayers(raw) {
  return Array.isArray(raw) ? raw : [raw];
}

/** Font family list (DTCG allows a string or an array). */
export function familyList(raw) {
  return Array.isArray(raw) ? raw : [raw];
}

/** True when two resolved values are the same. */
export function sameValue(a, b) {
  return JSON.stringify(a) === JSON.stringify(b);
}
