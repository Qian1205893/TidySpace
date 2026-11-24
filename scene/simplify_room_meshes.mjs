// Offline, opt-in. Install @gltf-transform/{core,extensions,functions}@4.5.1
// and meshoptimizer@1.0.1 in a separate directory and pass it as the first argument.
import { createRequire } from 'node:module';
import { mkdir, readdir, stat } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import assert from 'node:assert/strict';

const [deps, input, output, errorText = '0.005', ratioText = '0.3'] = process.argv.slice(2);
assert(deps && input && output && resolve(input) !== resolve(output),
    'usage: node simplify_room_meshes.mjs <npm-dir> <input-glb-dir> <output-glb-dir> [error] [ratio]');
const require = createRequire(resolve(deps, 'package.json'));
const { NodeIO } = require('@gltf-transform/core');
const { ALL_EXTENSIONS, KHRMaterialsUnlit } = require('@gltf-transform/extensions');
const { simplifyPrimitive } = require('@gltf-transform/functions');
const { MeshoptSimplifier } = require('meshoptimizer');
const io = new NodeIO().registerExtensions(ALL_EXTENSIONS);
const error = Number(errorText), ratio = Number(ratioText);
assert(error > 0 && error <= .02 && ratio > 0 && ratio < 1);
await MeshoptSimplifier.ready;
await mkdir(output, { recursive: true });

function triangles(p) {
    return p.getMode() === 4 ? (p.getIndices()?.getCount() ?? p.getAttribute('POSITION').getCount()) / 3 : 0;
}

const audit = [];
const architecture = new Set(['tidy-renovation.glb', 'tidy-study-room.glb', 'tidy-gaming-room.glb']);
for (const file of (await readdir(input)).sort()) {
    if (!file.endsWith('.glb') || file === 'tidy-cleaning-expanded.glb') continue;
    const source = join(input, file), destination = join(output, file);
    const doc = await io.read(source);
    const root = doc.getRoot();
    assert(!root.listSkins().length && !root.listAnimations().length, file);
    const primitives = root.listMeshes().flatMap(m => m.listPrimitives());
    const before = primitives.reduce((sum, p) => sum + triangles(p), 0);
    const selected = primitives.filter(p => triangles(p) >= 1500 && p.getIndices() && !p.listTargets().length);
    let reduced = 0;
    for (const p of selected) {
        const count = triangles(p);
        const pos = p.getAttribute('POSITION');
        const min = pos.getMin([]), max = pos.getMax([]);
        const span = Math.max(...max.map((v, i) => v - min[i]));
        const probe = doc.createMesh().addPrimitive(p.clone());
        const q = probe.listPrimitives()[0];
        // Keep primitive boundaries locked, including seams between adjacent parts.
        simplifyPrimitive(q, { simplifier: MeshoptSimplifier, ratio, error, lockBorder: true });
        const low = q.getAttribute('POSITION').getMin([]), high = q.getAttribute('POSITION').getMax([]);
        if (triangles(q) > 0 && triangles(q) <= count * .95 &&
            low.every((v, i) => Math.abs(v - min[i]) <= span * error) &&
            high.every((v, i) => Math.abs(v - max[i]) <= span * error)) {
            p.setIndices(q.getIndices());
            for (const semantic of p.listSemantics()) p.setAttribute(semantic, q.getAttribute(semantic));
            reduced++;
        }
        q.dispose();
        probe.dispose();
    }
    const convert = architecture.has(file);
    if (convert) {
        const fixed = [], other = new Set();
        for (const scene of root.listScenes()) {
            for (const node of scene.listChildren()) {
                const legacy = file === 'tidy-renovation.glb' && node.getName() !== 'renovation_house';
                node.traverse(child => child.getMesh()?.listPrimitives().forEach(p => {
                    if (legacy) other.add(p.getMaterial()); else fixed.push(p);
                }));
            }
        }
        const unlit = doc.createExtension(KHRMaterialsUnlit);
        const converted = new Map();
        for (const p of fixed) {
            const original = p.getMaterial();
            // The generated railing still expects its authored PBR finish.
            if (!original || original.getName() === 'Atelier | Ivory painted joinery') continue;
            if (!converted.has(original)) {
                // Split materials shared with the retired cleaning scene,
                // keeping its independent PBR path compatible.
                const mat = other.has(original) ? original.clone() : original;
                mat.setExtension('KHR_materials_unlit', unlit.createUnlit());
                mat.setNormalTexture(null).setOcclusionTexture(null).setMetallicRoughnessTexture(null);
                converted.set(original, mat);
            }
            p.setMaterial(converted.get(original));
        }
        for (const texture of root.listTextures()) {
            if (texture.listParents().every(parent => parent === root)) texture.dispose();
        }
    }
    if (reduced || convert) {
        for (const accessor of root.listAccessors()) {
            if (accessor.listParents().every(parent => parent === root)) accessor.dispose();
        }
        await io.write(destination, doc);
    }
    audit.push({ file, before, after: primitives.reduce((sum, p) => sum + triangles(p), 0), reduced,
        bytesBefore: (await stat(source)).size, bytesAfter: (await stat(reduced || convert ? destination : source)).size });
}
console.log(JSON.stringify(audit, null, 2));
