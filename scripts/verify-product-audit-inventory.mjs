#!/usr/bin/env node
import fs from 'node:fs';

const matrixPath = 'docs/TOSS_PARITY_MATRIX.md';
const auditPath = 'docs/PRODUCT_COMPLETENESS_AUDIT.md';

function tableRows(source, startMarker, endMarker, columnCount) {
  const start = source.indexOf(startMarker);
  if (start < 0) throw new Error(`Missing section: ${startMarker}`);
  const end = endMarker ? source.indexOf(endMarker, start) : source.length;
  if (end < 0) throw new Error(`Missing section boundary: ${endMarker}`);
  return source.slice(start, end).split('\n')
    .filter(line => line.startsWith('| ') && !line.startsWith('|---'))
    .slice(1)
    .map(line => {
      const cells = [];
      let cellStart = 2;
      for (let i = 2; i < line.length && cells.length < columnCount; i++) {
        if (line[i] === '|') {
          cells.push(line.slice(cellStart, i).trim());
          cellStart = i + 1;
        }
      }
      cells.push(line.slice(cellStart).replace(/\|\s*$/, '').trim());
      return cells;
    });
}

function normalize(value) {
  return value.replace(/\s+/g, ' ').trim();
}

const matrix = tableRows(fs.readFileSync(matrixPath, 'utf8'), '## Product Area Matrix', '## Non-Negotiable Gates Before Real Money', 3)
  .map(c => ({ area: normalize(c[0]), capability: normalize(c[1]), adaptation: normalize(c[2]) }));
const audit = tableRows(fs.readFileSync(auditPath, 'utf8'), '## Full product inventory', '## Platform surfaces', 4)
  .map(c => ({ area: normalize(c[0]).replace(/ \(\d+\)$/, ''), capability: normalize(c[1]), adaptation: normalize(c[3]) }));

const errors = [];
if (matrix.length !== audit.length) errors.push(`row count mismatch: parity matrix=${matrix.length}, audit=${audit.length}`);
for (let i = 0; i < Math.min(matrix.length, audit.length); i++) {
  for (const key of ['area', 'capability', 'adaptation']) {
    if (matrix[i][key] !== audit[i][key]) {
      errors.push(`row ${i + 1} ${key} mismatch: parity matrix=${JSON.stringify(matrix[i][key])}, audit=${JSON.stringify(audit[i][key])}`);
    }
  }
}
if (errors.length) {
  console.error('Product completeness inventory is out of sync:');
  for (const error of errors.slice(0, 30)) console.error(`- ${error}`);
  if (errors.length > 30) console.error(`- ... and ${errors.length - 30} more`);
  process.exit(1);
}
console.log(`Product completeness inventory verified: all ${matrix.length} product rows match the parity matrix in order.`);
