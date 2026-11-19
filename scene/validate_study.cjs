const fs=require('fs'),path=require('path'),crypto=require('crypto');
const validator=require('../.scratch/glb-validation/node_modules/gltf-validator');
const root=path.resolve(__dirname,'..'),out=path.join(root,'../TidySpace-study-renovation-20261116');
async function main(){
 const reports={};
 for(const name of ['tidy-study','tidy-study-room','tidy-home-expansion']){
  const bytes=fs.readFileSync(path.join(root,'app/src/main/assets/models',name+'.glb'));
  const g=JSON.parse(bytes.subarray(20,20+bytes.readUInt32LE(12)).toString());
  const report=await validator.validateBytes(new Uint8Array(bytes),{uri:name+'.glb',maxIssues:100});
  fs.writeFileSync(path.join(out,name+'-validation.json'),JSON.stringify(report,null,2));
  const names=g.nodes.map(n=>n.name),retired=['washer','bathvanity','toilet','bathtub','mirror','laundrybasket'];
  if(names.some(n=>retired.includes(n)||n.startsWith('bathroom_')))throw Error('Retired geometry present');
  if(report.issues.numErrors)throw Error(name+' invalid');
  reports[name]={bytes:bytes.length,sha256:crypto.createHash('sha256').update(bytes).digest('hex'),
   errors:report.issues.numErrors,warnings:report.issues.numWarnings,
   triangles:g.meshes.reduce((n,m)=>n+m.primitives.reduce((n,p)=>n+g.accessors[p.indices].count/3,0),0)};
 }
 if(fs.existsSync(path.join(root,'app/src/main/assets/models/tidy-bathroom.glb')))throw Error('Bathroom file remains');
 const manifest=JSON.parse(fs.readFileSync('/tmp/tidyspace-study-20261116/study-manifest.json'));
 if(Object.keys(manifest).length!==14)throw Error('Model count');
 for(const [name,m] of Object.entries(manifest))
  if(m.size.some((s,k)=>Math.abs(m.max[k]-m.min[k]-s)>1e-5||Math.abs(m.max[k]+m.min[k])>1e-5))throw Error(name+' bounds');
 fs.writeFileSync(path.join(out,'asset-validation.json'),JSON.stringify(reports,null,2));
 fs.writeFileSync(path.join(out,'study-model-manifest.json'),JSON.stringify(manifest,null,2));
 console.log(JSON.stringify(reports,null,2));
}
main().catch(e=>{console.error(e);process.exitCode=1});
