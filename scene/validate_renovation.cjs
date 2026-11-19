const fs=require('fs');
const path=require('path');
const validator=require('../.scratch/glb-validation/node_modules/gltf-validator');
const root=path.resolve(__dirname,'..');
const bytes=fs.readFileSync(path.join(root,'app/src/main/assets/models/tidy-renovation.glb'));
validator.validateBytes(new Uint8Array(bytes),{uri:'tidy-renovation.glb'}).then(report=>{
  fs.writeFileSync(path.join(root,'.scratch/renovation-gltf-validation.json'),JSON.stringify(report,null,2));
  const jsonLength=bytes.readUInt32LE(12);
  const gltf=JSON.parse(bytes.subarray(20,20+jsonLength).toString());
  const names=new Set(gltf.nodes.map(n=>n.name));
  const required=['renovation_house','renovation_base','renovation_fireplace','clean_bin','clean_stand',
    'clean_Vacuum','clean_Broom','clean_Brush'];
  for(const kind of ['dust','debris','wall','trash']) for(let i=0;i<(kind==='trash'?3:2);i++) {
    required.push(`${kind}-${i}`);
    if(kind==='debris') for(let j=0;j<8;j++) required.push(`${kind}-${i}_wipe-${j}`);
    if(kind==='dust'||kind==='wall') required.push(`${kind}-${i}_mesh`);
    if(kind==='debris') for(let j=0;j<28;j++) required.push(`${kind}-${i}_piece-${j}`);
  }
  required.push('broom_bristles','brush_bristles');
  const missing=required.filter(n=>!names.has(n));
  const surfaces={};
  for(const kind of ['dust','wall']) for(let i=0;i<2;i++) {
    const id=`${kind}-${i}`;
    const mesh=gltf.meshes[gltf.nodes.find(n=>n.name===`${id}_mesh`).mesh];
    const materials=mesh.primitives.map(p=>gltf.materials[p.material]);
    surfaces[id]={materialSlots:materials.length,
      passed:materials.length===24 && new Set(materials.map(m=>m.name)).size===24 &&
        materials.every(m=>m.alphaMode==='BLEND' && m.name.startsWith(`${id} cell `))};
  }
  const summary={errors:report.issues.numErrors,warnings:report.issues.numWarnings,
    nodes:gltf.nodes.length,requiredNodes:required.length,missing,bytes:bytes.length,
    surfaces,
    triangles:gltf.meshes.reduce((sum,m)=>sum+m.primitives.reduce((n,p)=>n+gltf.accessors[p.indices].count/3,0),0)};
  fs.writeFileSync(path.join(root,'.scratch/renovation-asset-check.json'),JSON.stringify(summary,null,2));
  console.log(summary);
  if(summary.errors || summary.warnings || missing.length || Object.values(surfaces).some(v=>!v.passed)) process.exitCode=1;
});
