/* Validate current delivered contracts; old removed window scenery is forbidden. */
const fs=require('fs'),path=require('path'),crypto=require('crypto');
const validator=require('../.scratch/glb-validation/node_modules/gltf-validator');
const root=path.resolve(__dirname,'..'),out=path.join(root,'cleaning-play-refresh-20261115');
const assets=path.join(root,'app/src/main/assets/models');
const families={
  'tidy-furniture.glb':['chair','sofa','desk','lamp'],
  'tidy-renovation.glb':['renovation_house','renovation_base','renovation_fireplace',
    'clean_Vacuum','clean_Broom','clean_Brush','broom_bristles','brush_bristles'],
  'tidy-moving-home.glb':['entryway','hearth_fire','room_pendant','house_clock','house_dimmer','house_journal'],
  'tidy-playthings.glb':['mochi','mochi_soft','jelly','jelly_soft','tumbler','tumbler_body'],
  'tidy-toys.glb':['woodfish','musicbox','bowl'],
  'tidy-home-expansion.glb':Object.keys(JSON.parse(fs.readFileSync(path.join(__dirname,'home-expansion/manifest.json')))),
};
async function main() {
  const all={};
  for(const [file,required] of Object.entries(families)) {
    const bytes=fs.readFileSync(path.join(assets,file)),length=bytes.readUInt32LE(12);
    const gltf=JSON.parse(bytes.subarray(20,20+length).toString());
    const report=await validator.validateBytes(new Uint8Array(bytes),{uri:file,maxIssues:100});
    const names=new Set(gltf.nodes.map(n=>n.name));
    all[file]={errors:report.issues.numErrors,warnings:report.issues.numWarnings,
      missingNodes:required.filter(n=>!names.has(n)),
      windowSceneryNodes:[...names].filter(n=>/^window_(back|side)_(day|sunset|night)(?:_mesh)?$/.test(n)),
      sha256:crypto.createHash('sha256').update(bytes).digest('hex'),bytes:bytes.length};
    if(file==='tidy-renovation.glb') {
      const quiet=['Warm mineral plaster','Olive mineral paint','Ivory painted joinery','Lime mortar'];
      all[file].quietFinishesPassed=quiet.every(name=>{
        const material=gltf.materials.find(m=>m.name===`Atelier | ${name}`);
        return material && !material.normalTexture && material.pbrMetallicRoughness.baseColorTexture;
      });
      if(!all[file].quietFinishesPassed) process.exitCode=1;
    }
    if(all[file].errors||all[file].warnings||all[file].missingNodes.length||all[file].windowSceneryNodes.length)
      process.exitCode=1;
  }
  const manifest=JSON.parse(fs.readFileSync(path.join(__dirname,'renovation/manifest.json')));
  const broom=manifest.clean_Broom;
  all.toolBounds={broomFloorMin:broom.min[1],broomWidth:broom.max[0]-broom.min[0],
    passed:broom.min[1]>=-.016 && broom.max[0]-broom.min[0]<=.174};
  if(!all.toolBounds.passed) process.exitCode=1;
  fs.writeFileSync(path.join(out,'all-assets-validation.json'),JSON.stringify(all,null,2));
  console.log(JSON.stringify(all,null,2));
}
main().catch(e=>{console.error(e);process.exitCode=1});
