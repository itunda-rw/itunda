import fs from 'node:fs';
import path from 'node:path';

const root=process.cwd();
const source=path.join(root,'design-system/components/contract-manifest.json');
const output=path.join(root,'business/developers/design/contract-manifest.json');
const manifest=JSON.parse(fs.readFileSync(source,'utf8'));

const canonicalScenarios={
  button:['default','pressed','disabled','loading','long'],
  field:['default','focus','error','success','long'],
  select:['placeholder','focus','disabled','error','success','option-disabled'],
  checkbox:['checked','indeterminate','disabled','error'],
  radio:['checked','disabled','error','group'],
  switch:['on','off','loading','disabled','error'],
  tabs:['selected','disabled','controls','roving-focus'],
  empty:['default','error','success','long'],
};
const platforms=new Set(['web','android','ios']);
const failures=[];
for(const component of manifest.components||[]){
  const prefix=''+(component?.id||'<missing-id>');
  const kind=component.id==='text-field'?'field':component.id==='empty-state'?'empty':component.id;
  const expected=canonicalScenarios[kind]||[];
  const scenarios=component?.qa?.scenarios;
  if(!scenarios||typeof scenarios!=='object'||Array.isArray(scenarios)){failures.push(prefix+': qa.scenarios must be an object');continue;}
  for(const id of expected){
    const scenario=scenarios[id];
    if(!scenario||typeof scenario!=='object'||Array.isArray(scenario)){failures.push(prefix+': qa.scenarios.'+id+' must be an object');continue;}
    if(!Array.isArray(scenario.platforms)||scenario.platforms.length===0){failures.push(prefix+': qa.scenarios.'+id+'.platforms must be a non-empty array');continue;}
    if(scenario.platforms.some(p=>!platforms.has(String(p)))) failures.push(prefix+': qa.scenarios.'+id+'.platforms contains invalid platform');
    if(scenario.platforms.some(p=>!(component.platforms||[]).includes(p))) failures.push(prefix+': qa.scenarios.'+id+'.platforms exceeds component platforms');
    if(new Set(scenario.platforms).size!==scenario.platforms.length) failures.push(prefix+': qa.scenarios.'+id+'.platforms contains duplicates');
  }
  for(const id of Object.keys(scenarios)) if(!expected.includes(id)) failures.push(prefix+': qa.scenarios contains non-canonical scenario '+id);
}
if(failures.length){
  console.error('IDS contract sync blocked by validation errors:');
  for(const failure of failures) console.error(' - '+failure);
  process.exit(1);
}
fs.writeFileSync(output,JSON.stringify(manifest,null,2)+'\n');
console.log('IDS contract validation: PASS');
console.log('Synced IDS contract manifest:',path.relative(root,output));
