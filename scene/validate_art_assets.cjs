/* Verify actual exported bytes and the pre-refresh node/geometry contracts. */
const fs = require('fs');
const path = require('path');
const validator = require('../.scratch/glb-validation/node_modules/gltf-validator');
const root = path.resolve(__dirname, '..');
const openWindows = process.argv.includes('--open-windows');
const out = path.join(root, openWindows ? 'interaction-refresh-20261115' : 'visual-refresh-20261115');
const removedWindowNode = name => openWindows && /^window_(back|side)_(day|sunset|night)(?:_mesh)?$/.test(name);
const baseline = JSON.parse(fs.readFileSync('/tmp/tidyspace-visual-20261115/baseline-inventory.json'));
fs.mkdirSync(out, {recursive: true});
async function main() {
  const result = {};
  for (const [name, old] of Object.entries(baseline)) {
    const bytes = fs.readFileSync(path.join(root, 'app/src/main/assets/models', name));
    const length = bytes.readUInt32LE(12);
    const gltf = JSON.parse(bytes.subarray(20, 20 + length).toString());
    const report = await validator.validateBytes(new Uint8Array(bytes), {uri: name, maxIssues: 100});
    fs.writeFileSync(path.join(out, name + '-validation.json'), JSON.stringify(report, null, 2));
    const names = new Set(gltf.nodes.map(n => n.name));
    const textures = gltf.images.map(image => {
      const view = gltf.bufferViews[image.bufferView];
      const png = bytes.subarray(28 + length + (view.byteOffset || 0));
      return {width: png.readUInt32BE(16), height: png.readUInt32BE(20)};
    });
    result[name] = {
      errors: report.issues.numErrors, warnings: report.issues.numWarnings,
      missingNodes: old.nodes.filter(n => !names.has(n) && !removedWindowNode(n)),
      windowSceneryNodes: [...names].filter(n => /^window_(back|side)_/.test(n)),
      nodes: gltf.nodes.length, primitives: gltf.meshes.reduce((s,m) => s+m.primitives.length,0),
      triangles: gltf.meshes.reduce((s,m) => s+m.primitives.reduce((v,p) => v+gltf.accessors[p.indices].count/3,0),0),
      bytes: bytes.length, previousBytes: old.bytes,
      textureImages: textures.length,
      decodedTextureBytes: textures.reduce((s,t) => s+t.width*t.height*4,0),
      pbrWithOcclusion: gltf.materials.filter(m => m.occlusionTexture).length,
      maxTextureSize: Math.max(...textures.map(t => Math.max(t.width,t.height))),
    };
    if (report.issues.numErrors || result[name].missingNodes.length) process.exitCode = 1;
    if (openWindows && result[name].windowSceneryNodes.length) process.exitCode = 1;
  }
  // Local author bounds are independent of staged .blend inspection placement.
  const families = ['furniture','moving-home','playthings','renovation','toys'];
  for (const family of families) {
    const before = JSON.parse(fs.readFileSync(`/tmp/tidyspace-visual-20261115/baseline/${family}-manifest.json`));
    const after = JSON.parse(fs.readFileSync(path.join(__dirname,family,'manifest.json')));
    const oldItems = before.items || before, newItems = after.items || after;
    const deviations = [];
    for (const [name,bounds] of Object.entries(oldItems)) {
      if (!bounds.min) continue;
      const now = newItems[name];
      if (!now) { if (!removedWindowNode(name)) deviations.push({name,missing: true}); continue; }
      const delta = Math.max(...['min','max'].flatMap(k => bounds[k].map((v,i) => Math.abs(v-now[k][i]))));
      if (delta > .001) deviations.push({name,delta});
    }
    result[family+'-bounds'] = {deviations};
    if (deviations.length) process.exitCode = 1;
  }
  fs.writeFileSync(path.join(out,'asset-validation.json'), JSON.stringify(result,null,2));
  console.log(JSON.stringify(result,null,2));
}
main().catch(error => { console.error(error); process.exitCode = 1; });
