import fs from 'node:fs';
import path from 'node:path';

const root=process.cwd();
const source=path.join(root,'design-system/components/contract-manifest.json');
const output=path.join(root,'business/developers/design/contract-manifest.json');
const manifest=JSON.parse(fs.readFileSync(source,'utf8'));
fs.writeFileSync(output,JSON.stringify(manifest,null,2)+'\n');
console.log('Synced IDS contract manifest:',path.relative(root,output));
