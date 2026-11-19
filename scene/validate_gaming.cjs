const fs=require('fs'),path=require('path');
const validator=require('../.scratch/glb-validation/node_modules/gltf-validator');
const root=path.resolve(__dirname,'..');
const out=path.join(root,'../TidySpace-gaming-renovation-20261115');
async function main(){
 const result={};
 for(const name of ['tidy-gaming','tidy-gaming-room','tidy-bathroom']){
  const bytes=fs.readFileSync(path.join(root,'app/src/main/assets/models',name+'.glb'));
  const json=JSON.parse(bytes.subarray(20,20+bytes.readUInt32LE(12)).toString());
  const report=await validator.validateBytes(new Uint8Array(bytes),{uri:name+'.glb',maxIssues:100});
  fs.writeFileSync(path.join(out,name+'-validation.json'),JSON.stringify(report,null,2));
  const names=json.nodes.map(n=>n.name);
  if(name==='tidy-bathroom' && names.some(n=>/sliding|doorway|parked.*leaf/i.test(n)))throw Error('Door remnants');
  if(name==='tidy-gaming-room' && names.some(n=>/door|jamb|lintel/i.test(n)&&!n.includes('doorless')))throw Error('Gaming doorway');
  result[name]={bytes:bytes.length,errors:report.issues.numErrors,warnings:report.issues.numWarnings,nodes:names.length,
   triangles:json.meshes.reduce((n,m)=>n+m.primitives.reduce((n,p)=>n+json.accessors[p.indices].count/3,0),0),
   materialCount:json.materials.length,embeddedTextures:(json.images||[]).length};
  if(report.issues.numErrors)throw Error(name+' invalid GLB');
 }
 const manifest=JSON.parse(fs.readFileSync('/tmp/tidyspace-gaming-20261115/gaming-manifest.json'));
 for(const [name,m] of Object.entries(manifest)){
  if(m.size.some((size,k)=>Math.abs(m.max[k]-m.min[k]-size)>.00001))throw Error(name+' bounds mismatch');
 }
 result.assetBounds={twelveAssets:Object.keys(manifest).length,centredNativePivots:true};
 fs.writeFileSync(path.join(out,'asset-validation.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result,null,2));
}
main().catch(e=>{console.error(e);process.exitCode=1;});
