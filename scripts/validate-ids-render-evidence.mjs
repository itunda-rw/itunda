import { readFile } from 'node:fs/promises';
import { join } from 'node:path';

const root=process.cwd();
const matrix=JSON.parse(await readFile(join(root,'design-system/qa/render-matrix.json'),'utf8'));
const schema=JSON.parse(await readFile(join(root,'design-system/qa/evidence-schema.json'),'utf8'));
const evidencePath=process.argv[2];

if(!evidencePath){
  console.error('Usage: node scripts/validate-ids-render-evidence.mjs <ids-qa-evidence.json>');
  process.exit(2);
}

const payload=JSON.parse(await readFile(join(root,evidencePath),'utf8'));
const failures=[];
const platforms=['web','android','ios'];

if(payload?.schema!==schema.schema) failures.push('Evidence schema mismatch: '+payload?.schema);
if(!Array.isArray(payload?.evidence)) failures.push('Evidence payload must contain an evidence array.');

const records=Array.isArray(payload?.evidence)?payload.evidence:[];
const key=(platform,scenario)=>platform+'::'+scenario;
const byKey=new Map(records.map(record=>[key(record.platform,record.scenario),record]));

for(const componentId of matrix.requiredComponents){
  const scenarios=matrix.scenarioSets?.[componentId]||[];
  for(const scenario of scenarios){
    for(const platform of platforms){
      const record=byKey.get(key(platform,scenario));
      if(!record) {
        failures.push('Missing rendered evidence: '+platform+' '+scenario);
        continue;
      }
      if(record.componentId!==componentId) failures.push('Wrong component for '+platform+' '+scenario);
      for(const field of schema.evidenceRequired){
        if(record[field]===undefined||record[field]===null||record[field]==='') failures.push('Missing '+field+' for '+platform+' '+scenario);
      }
      for(const field of schema.contextRequired){
        if(record.context?.[field]===undefined||record.context?.[field]===null||record.context?.[field]==='') failures.push('Missing context.'+field+' for '+platform+' '+scenario);
      }
      if(!platforms.includes(record.platform)) failures.push('Invalid platform: '+record.platform);
      if(Number.isNaN(Date.parse(String(record.verifiedAt)))) failures.push('Invalid verifiedAt for '+platform+' '+scenario);
    }
  }
}

const expected=matrix.requiredComponents.reduce((sum,id)=>sum+(matrix.scenarioSets?.[id]?.length||0),0)*platforms.length;
const unique=byKey.size;
const result={schema:'ids-render-evidence-validation/v1',expectedEvidenceRecords:expected,uniqueEvidenceRecords:unique,complete:failures.length===0&&unique===expected,failures};
console.log(JSON.stringify(result,null,2));
if(failures.length) process.exit(1);
