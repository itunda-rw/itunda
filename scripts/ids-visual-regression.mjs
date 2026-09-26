import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { inflateSync, deflateSync } from 'node:zlib';

const input=process.argv[2]||'/tmp/ids-visual-matrix.json';
const baselinePath=process.argv[3]||'design-system/components/visual-baseline.json';
const update=process.argv.includes('--update');
const matrix=JSON.parse(readFileSync(input,'utf8'));
const cases=matrix.cases||[];
const diffDir='/tmp/ids-visual-diff';

const chunks=b=>{
  const out=[]; let p=8;
  while(p<b.length){const n=b.readUInt32BE(p),t=b.subarray(p+4,p+8).toString();out.push([t,b.subarray(p+8,p+8+n)]);p+=n+12}
  return out;
};
const decode=b=>{
  if(b.readUInt32BE(0)!==0x89504e47)throw Error('Not PNG');
  const cs=chunks(b),h=cs.find(x=>x[0]==='IHDR')?.[1];
  if(!h||h[8]!==8||h[9]!==6||h[12]!==0)throw Error('Expected 8-bit RGBA PNG');
  const w=h.readUInt32BE(0),ht=h.readUInt32BE(4),stride=w*4;
  const raw=inflateSync(Buffer.concat(cs.filter(x=>x[0]==='IDAT').map(x=>x[1])));
  const out=Buffer.alloc(ht*stride);let p=0,prev=Buffer.alloc(stride);
  for(let y=0;y<ht;y++){const f=raw[p++],row=raw.subarray(p,p+stride);p+=stride;const dst=out.subarray(y*stride,(y+1)*stride);
    for(let x=0;x<stride;x++){const l=x>=4?dst[x-4]:0,u=prev[x]||0,ul=x>=4?prev[x-4]:0;let v=row[x];
      if(f===1)v=(v+l)&255;else if(f===2)v=(v+u)&255;else if(f===3)v=(v+Math.floor((l+u)/2))&255;
      else if(f===4){const q=l+u-ul,pa=Math.abs(q-l),pb=Math.abs(q-u),pc=Math.abs(q-ul);v=(v+(pa<=pb&&pa<=pc?l:pb<=pc?u:ul))&255}
      else if(f!==0)throw Error('Unsupported PNG filter '+f);dst[x]=v}prev=dst}
  return{width:w,height:ht,data:out};
};
const crc=b=>{let c=0xffffffff;for(const x of b){c^=x;for(let i=0;i<8;i++)c=(c>>>1)^(0xedb88320&-(c&1))}return(c^0xffffffff)>>>0};
const chunk=(t,d)=>{const n=Buffer.from(t),b=Buffer.alloc(12+d.length);b.writeUInt32BE(d.length);n.copy(b,4);d.copy(b,8);b.writeUInt32BE(crc(Buffer.concat([n,d])),8+d.length);return b};
const encode=({width,height,data})=>{const stride=width*4,raw=Buffer.alloc(height*(stride+1));for(let y=0;y<height;y++)data.copy(raw,y*(stride+1)+1,y*stride,(y+1)*stride);const h=Buffer.alloc(13);h.writeUInt32BE(width);h.writeUInt32BE(height,4);h[8]=8;h[9]=6;return Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',h),chunk('IDAT',deflateSync(raw)),chunk('IEND',Buffer.alloc(0))])};
const diff=(a,b,threshold=8)=>{
  if(a.width!==b.width||a.height!==b.height)return{ratio:1,changed:a.width*a.height,total:a.width*a.height,image:null,sizeChanged:true};
  const d=Buffer.alloc(a.data.length);let changed=0,total=a.width*a.height;
  for(let i=0;i<a.data.length;i+=4){const delta=Math.max(Math.abs(a.data[i]-b.data[i]),Math.abs(a.data[i+1]-b.data[i+1]),Math.abs(a.data[i+2]-b.data[i+2]),Math.abs(a.data[i+3]-b.data[i+3]));
    if(delta>threshold){changed++;d[i]=255;d[i+2]=255;d[i+3]=255}else{const v=Math.round((b.data[i]+b.data[i+1]+b.data[i+2])/3);d[i]=v;d[i+1]=v;d[i+2]=v;d[i+3]=255}}
  return{ratio:changed/total,changed,total,image:{width:a.width,height:a.height,data:d},sizeChanged:false};
};
const key=c=>[c.component,c.state,c.theme,c.scale,c.viewport,c.motion].join('|');
const current=Object.fromEntries(cases.map(c=>[key(c),readFileSync('/tmp/ids-visual-matrix/'+c.filename+'.png')]));

if(!existsSync(baselinePath)||update){
  const payload={version:'2.0.0',generatedFrom:matrix.source,pixelThreshold:{channel:8,allowedChangedRatio:0.001},cases:{}};
  for(const[k,png]of Object.entries(current))payload.cases[k]={pngBase64:png.toString('base64')};
  writeFileSync(baselinePath,JSON.stringify(payload,null,2)+'\n');
  console.log(update?'IDS pixel baseline updated intentionally:':'IDS pixel baseline initialized:',Object.keys(current).length,'cases');
  process.exit(0);
}

const baseline=JSON.parse(readFileSync(baselinePath,'utf8')),expected=baseline.cases||{},limit=baseline.pixelThreshold?.allowedChangedRatio??0.001,failures=[];
const {mkdirSync}=await import('node:fs');mkdirSync(diffDir,{recursive:true});
for(const[k,png]of Object.entries(current)){
  if(!expected[k]?.pngBase64){failures.push('new visual case: '+k);continue}
  const d=diff(decode(Buffer.from(expected[k].pngBase64,'base64')),decode(png));
  if(d.sizeChanged||d.ratio>limit){
    failures.push('pixel drift: '+k+' — '+d.changed+'/'+d.total+' pixels ('+(d.ratio*100).toFixed(4)+'%)');
    const safe=k.replace(/[^a-zA-Z0-9._-]+/g,'_');
    writeFileSync(diffDir+'/'+safe+'-actual.png',png);
    if(d.image)writeFileSync(diffDir+'/'+safe+'-diff.png',encode(d.image));
  }
}
for(const k of Object.keys(expected))if(!(k in current))failures.push('visual case removed: '+k);
writeFileSync(diffDir+'/summary.json',JSON.stringify({failures,threshold:limit},null,2)+'\n');
if(failures.length){console.error('IDS pixel visual regression failed.');failures.forEach(x=>console.error(' - '+x));process.exit(1)}
console.log('IDS pixel visual regression passed:',Object.keys(current).length,'cases within',limit*100+'% changed-pixel threshold.');
