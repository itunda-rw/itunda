#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
const root=process.cwd();
const map=JSON.parse(fs.readFileSync(path.join(root,"client/design-system/token-parity.json"),"utf8"));
const required=map.semanticRoles;
const files={
 web:"client/design-system/tokens.css",
 android:"android/core/designsystem/src/main/java/rw/itunda/core/designsystem/theme/IdsSemanticColors.kt",
 ios:"ios/Core/DesignSystem/Sources/IDS.swift"
};
const failures=[];
const camelToKebab=s=>s.replace(/[A-Z]/g,m=>"-"+m.toLowerCase());
for(const role of required){
 for(const [platform,file] of Object.entries(files)){
  const text=fs.readFileSync(path.join(root,file),"utf8");
  const ok=platform==="web"
   ? new RegExp("--itunda-"+camelToKebab(role)+"\\b").test(text)
   : new RegExp("\\b"+role+"\\b").test(text);
  if(!ok) failures.push(platform+": missing semantic role "+role+" in "+file);
 }
}
for(const [platform,file] of Object.entries(files)){
 const text=fs.readFileSync(path.join(root,file),"utf8");
 const anchor=platform==="ios" ? "7472F4" : "#7472F4";
 if(!text.includes(anchor)) failures.push(platform+": missing light brand anchor #7472F4");
}
if(failures.length){console.error("IDS semantic parity: FAIL"); failures.forEach(x=>console.error(" - "+x)); process.exit(1);}
console.log("IDS semantic parity: PASS");
console.log("Checked "+required.length+" semantic roles across Web, Android and iOS.");
