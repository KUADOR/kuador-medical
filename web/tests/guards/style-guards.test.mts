// ADR-007 · Automatic style guards of the web piece.
// Hand-written colors and pixel sizes must fail the lint (and with it `npm run build` and `npm test`).
// Only the generated tokens (src/styles/tokens.css) and the design system library
// (src/app/design-system/) may hold pixel values; nothing outside the tokens may hold a color literal.
import { ESLint } from 'eslint';
import { fileURLToPath } from 'node:url';
import stylelint from 'stylelint';
import { describe, expect, it } from 'vitest';

const webRoot = fileURLToPath(new URL('../../', import.meta.url));
const fixture = (name: string) => fileURLToPath(new URL(`./fixtures/${name}`, import.meta.url));

async function lintStyle(code: string, file = 'src/app/features/example/example.page.scss') {
  const result = await stylelint.lint({ code, codeFilename: `${webRoot}${file}`, cwd: webRoot });
  return result.results.flatMap((r) => r.warnings.map((w) => w.rule));
}

async function lintCode(code: string, file: string) {
  const eslint = new ESLint({ cwd: webRoot });
  const [result] = await eslint.lintText(code, { filePath: `${webRoot}${file}` });
  return result.messages.map((m) => m.ruleId);
}

describe('style lint (Stylelint)', () => {
  it('fails on a test file with a hex color', async () => {
    const result = await stylelint.lint({ files: [fixture('hex-color.scss')], cwd: webRoot });

    expect(result.errored).toBe(true);
    expect(result.results[0].warnings.map((w) => w.rule)).toEqual(['color-no-hex']);
  });

  it.each([
    ['a hex color', '.a { color: #fff; }', 'color-no-hex'],
    ['a named color', '.a { color: red; }', 'color-named'],
    ['a color function', '.a { background: rgba(0, 0, 0, 0.5); }', 'function-disallowed-list'],
    ['a pixel size', '.a { padding: 12px; }', 'unit-disallowed-list'],
    ['a pixel radius', '.a { border-radius: 4px; }', 'unit-disallowed-list'],
    ['a remote font or image', ".a { background: url('https://cdn.example.com/a.png'); }", 'function-url-scheme-disallowed-list'],
  ])('rejects %s outside the tokens', async (_name, code, rule) => {
    expect(await lintStyle(code)).toContain(rule);
  });

  it('accepts styles that only read tokens', async () => {
    const code = `
      .a {
        padding: var(--kd-space-2) var(--kd-space-4);
        border: var(--kd-border-hairline);
        border-radius: var(--kd-radius-md);
        color: var(--kd-text-default);
        background: color-mix(in srgb, var(--kd-color-error) 10%, var(--kd-surface-raised));
        transition: opacity var(--kd-motion-duration-fast) var(--kd-motion-easing-standard);
      }
      .b { border-color: transparent; color: currentcolor; }
    `;

    expect(await lintStyle(code)).toEqual([]);
  });

  it('allows pixel values inside the design system library, but never color literals', async () => {
    const file = 'src/app/design-system/button/button.scss';

    expect(await lintStyle('.a { outline-offset: 2px; }', file)).toEqual([]);
    expect(await lintStyle('.a { color: #fff; }', file)).toContain('color-no-hex');
  });

  it('ignores the generated tokens', async () => {
    const result = await stylelint.lint({ files: ['src/styles/tokens.css'], cwd: webRoot });

    expect(result.results.every((r) => r.ignored)).toBe(true);
  });

  it('passes on every stylesheet of the app', async () => {
    const result = await stylelint.lint({ files: ['src/**/*.{css,scss}'], cwd: webRoot });
    const problems = result.results.flatMap((r) =>
      r.warnings.map((w) => `${r.source}:${w.line} ${w.text}`),
    );

    expect(problems).toEqual([]);
  });
});

describe('template and component lint (angular-eslint)', () => {
  it('rejects inline styles in a template file', async () => {
    const html = '<p style="color: var(--kd-text-muted)">Hola</p>';

    expect(await lintCode(html, 'src/app/features/example/example.page.html')).toContain(
      '@angular-eslint/template/no-inline-styles',
    );
  });

  it('rejects style bindings in an inline template', async () => {
    const ts = `
      import { Component } from '@angular/core';
      @Component({ selector: 'mk-example', template: '<p [style.margin]="m">Hola</p>' })
      export class Example { m = '1rem'; }
    `;

    expect(await lintCode(ts, 'src/app/features/example/example.page.ts')).toContain(
      '@angular-eslint/template/no-inline-styles',
    );
  });

  it('rejects inline component styles, which the style lint cannot see', async () => {
    const ts = `
      import { Component } from '@angular/core';
      @Component({ selector: 'mk-example', template: '<p>Hola</p>', styles: '.a { color: red; }' })
      export class Example {}
    `;

    expect(await lintCode(ts, 'src/app/features/example/example.page.ts')).toContain(
      '@angular-eslint/component-max-inline-declarations',
    );
  });

  it('accepts a component with a template and a stylesheet file', async () => {
    const ts = `
      import { Component } from '@angular/core';
      @Component({ selector: 'mk-example', template: '<p class="a">Hola</p>', styleUrl: './example.page.scss' })
      export class Example {}
    `;

    expect(await lintCode(ts, 'src/app/features/example/example.page.ts')).toEqual([]);
  });

  it('passes on every template and component of the app', async () => {
    const results = await new ESLint({ cwd: webRoot }).lintFiles(['src']);
    const problems = results.flatMap((r) =>
      r.messages.map((m) => `${r.filePath}:${m.line} ${m.ruleId}`),
    );

    expect(problems).toEqual([]);
  });
});
