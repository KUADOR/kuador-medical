// @ts-check
// Style guards of the templates and components (ADR-007). This config only enforces the
// style rules; the style lint of the stylesheets is in .stylelintrc.json.
import angular from 'angular-eslint';
import { defineConfig } from 'eslint/config';
import tseslint from 'typescript-eslint';

export default defineConfig(
  {
    ignores: ['dist/**', '.angular/**', 'node_modules/**', 'src/**/*.d.ts'],
  },
  {
    files: ['src/**/*.ts'],
    languageOptions: { parser: tseslint.parser },
    plugins: { '@angular-eslint': angular.tsPlugin },
    // Lints inline templates with the template rules below.
    processor: angular.processInlineTemplates,
    rules: {
      // Component styles live in stylesheet files, where Stylelint checks them.
      '@angular-eslint/component-max-inline-declarations': [
        'error',
        { template: Number.MAX_SAFE_INTEGER, styles: 0, animations: Number.MAX_SAFE_INTEGER },
      ],
    },
  },
  {
    files: ['src/**/*.html'],
    languageOptions: { parser: angular.templateParser },
    plugins: { '@angular-eslint/template': angular.templatePlugin },
    rules: {
      // No style="", [style.x], [style] or [ngStyle]: styles come from classes and tokens.
      '@angular-eslint/template/no-inline-styles': 'error',
    },
  },
);
