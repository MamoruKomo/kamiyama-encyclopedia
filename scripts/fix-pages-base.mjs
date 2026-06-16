import { readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const basePath = '/kamiyama-encyclopedia';
const indexPath = join(process.cwd(), 'dist', 'index.html');

const html = readFileSync(indexPath, 'utf8')
  .replaceAll('href="/_expo/', `href="${basePath}/_expo/`)
  .replaceAll('src="/_expo/', `src="${basePath}/_expo/`);

writeFileSync(indexPath, html);
