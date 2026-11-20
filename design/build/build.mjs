// Usage: node build/build.mjs          writes the generated files
//        node build/build.mjs --check  fails if the versioned files are out of date
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { DESIGN_DIR } from './config.mjs';
import { generate } from './generate.mjs';

const REPO_ROOT = resolve(DESIGN_DIR, '..');
const check = process.argv.includes('--check');

try {
  const { files, contrast } = await generate();
  const stale = [];
  for (const [relativePath, content] of Object.entries(files)) {
    const target = resolve(REPO_ROOT, relativePath);
    if (check) {
      const current = await readFile(target, 'utf8').catch(() => null);
      if (current !== content) {
        stale.push(relativePath);
      }
    } else {
      await mkdir(dirname(target), { recursive: true });
      await writeFile(target, content);
      console.log(`wrote ${relativePath}`);
    }
  }
  console.log(`contrast: ${contrast.length} checks passed (WCAG AA, light and dark)`);
  if (stale.length > 0) {
    console.error(`Out of date, run "npm run build" in design/:\n  ${stale.join('\n  ')}`);
    process.exitCode = 1;
  }
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}
