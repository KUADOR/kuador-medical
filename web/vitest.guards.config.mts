// Node-side tests of the web tooling (style guards, ADR-007). They run before `ng test`
// through the `pretest` script; the Angular unit tests keep their own builder config.
import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    include: ['tests/guards/**/*.test.mts'],
    environment: 'node',
    testTimeout: 30_000,
  },
});
