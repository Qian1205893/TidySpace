/* Validate actual GLB bytes, all declared roots and the Kotlin animation pivots. */
const fs=require('fs'),path=require('path'),crypto=require('crypto');
const validator=require('../.scratch/glb-validation/node_modules/gltf-validator');
const root=path.resolve(__dirname,'..'),out=path.join(root,'play-home-expansion-20261115');
const manifest=JSON.parse(fs.readFileSync(path.join(__dirname,'home-expansion/manifest.json')));
const pivots=['slider_thumb',...Array.from({length:3},(_,i)=>`togglebox_${i}`),
  ...Array.from({length:5},(_,i)=>`abacus_${i}`),'bell_button','bell_dome','hourglass_rotor',
  'hourglass_sand_top','hourglass_sand_bottom','hourglass_stream','windmill_rotor','windmill_crank'];
async function main() {
  const all={};
  for(const name of fs.readdirSync(path.join(root,'app/src/main/assets/models')).filter(n=>n.endsWith('.glb'))) {
    const bytes=fs.readFileSync(path.join(root,'app/src/main/assets/models',name));
    const report=await validator.validateBytes(new Uint8Array(bytes),{uri:name,maxIssues:100});
    const len=bytes.readUInt32LE(12),gltf=JSON.parse(bytes.subarray(20,20+len).toString());
    const result={errors:report.issues.numErrors,warnings:report.issues.numWarnings,
      bytes:bytes.length,sha256:crypto.createHash('sha256').update(bytes).digest('hex'),
      triangles:gltf.meshes.reduce((s,m)=>s+m.primitives.reduce((a,p)=>a+gltf.accessors[p.indices].count/3,0),0),
      textureImages:gltf.images?.length||0,pbrMaterials:gltf.materials?.length||0};
    if(name==='tidy-home-expansion.glb') {
      const names=new Set(gltf.nodes.map(n=>n.name));
      result.newRoots=Object.keys(manifest).length;
      result.missingRoots=Object.keys(manifest).filter(n=>!names.has(n));
      result.missingPivots=pivots.filter(n=>!names.has(n));
      result.dimensions=Object.fromEntries(Object.entries(manifest).map(([name,v])=>[name,
        {size:v.max.map((p,i)=>p-v.min[i]),declared:v.declaredSize,
         maxOverrun:Math.max(...v.max.map((p,i)=>p-v.min[i]-v.declaredSize[i]))}]));
      if(result.missingRoots.length||result.missingPivots.length||
        Object.values(result.dimensions).some(v=>v.maxOverrun>.004)) process.exitCode=1;
      fs.writeFileSync(path.join(out,'home-glb-validator.json'),JSON.stringify(report,null,2));
    }
    all[name]=result;
    if(result.errors) process.exitCode=1;
  }
  fs.copyFileSync(path.join(__dirname,'home-expansion/manifest.json'),path.join(out,'new-model-manifest.json'));
  fs.writeFileSync(path.join(out,'asset-validation.json'),JSON.stringify(all,null,2));
  console.log(JSON.stringify(all,null,2));
}
main().catch(e=>{console.error(e);process.exitCode=1;});
