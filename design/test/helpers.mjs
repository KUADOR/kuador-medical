import { cp, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { TOKENS_DIR } from '../build/config.mjs';

/**
 * Copies design/tokens to a temporary folder, lets `mutate` edit the parsed files and
 * returns the folder. Call cleanup() when done.
 * @param {(files: Record<string, any>) => void} mutate keyed by file name
 */
export async function tokensFixture(mutate) {
  const dir = await mkdtemp(join(tmpdir(), 'kd-tokens-'));
  await cp(TOKENS_DIR, dir, { recursive: true });
  const names = [
    'border.tokens.json',
    'color.tokens.json',
    'contrast-pairs.json',
    'typography.tokens.json',
  ];
  const files = {};
  for (const name of names) {
    files[name] = JSON.parse(await readFile(join(dir, name), 'utf8'));
  }
  mutate(files);
  for (const name of names) {
    await writeFile(join(dir, name), JSON.stringify(files[name], null, 2));
  }
  return {
    tokensDir: dir,
    pairsFile: join(dir, 'contrast-pairs.json'),
    cleanup: () => rm(dir, { recursive: true, force: true }),
  };
}
