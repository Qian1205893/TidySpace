"""Shared, exported PBR art direction. No changes to roots, pivots or hit targets.

Run from the five Blender builders before their articulation-group mesh joins.
Textures are real PNGs; R/G/B pack cavity occlusion/roughness/metalness. Only
micro-surface occlusion is baked here: movable contact shadows stay dynamic.
"""
import bpy
import math
import sys
import os
import struct
import zlib
import hashlib
from pathlib import Path
import numpy as np
from mathutils import Vector
from mathutils.bvhtree import BVHTree

HERE = Path(__file__).resolve().parent
TEXTURES = HERE / "art-textures"
TEXTURES.mkdir(exist_ok=True)
PALETTE = {
    "oak": "A47B52", "walnut": "70503A", "cream": "DED3BE",
    "linen": "C9B89B", "slate": "496777", "mustard": "B88837",
    "brick": "A85D3E", "olive": "646B39", "jade": "758267",
    "lavender": "8C7C9E", "iron": "393A32", "brass": "B59452",
}
_materials = {}


def color(tone):
    return np.array([int(tone[i:i + 2], 16) / 255 for i in (0, 2, 4)])


def linear(c):
    return np.where(c <= .04045, c / 12.92, ((c + .055) / 1.055) ** 2.4)


def png(name, values, srgb=False):
    path = TEXTURES / (name + ".png")
    if not path.exists():
        h, w = values.shape[:2]
        # Write encoded PNG pixels explicitly. Blender's generated-image save
        # can persist linear floats without the expected sRGB conversion.
        # Encoding here avoids dark, orange wood from a second gamma transform.
        pixels = np.round(np.clip(values, 0, 1) * 255).astype(np.uint8)
        raw = b"".join(b"\0" + row.tobytes() for row in pixels)
        def chunk(tag, data):
            return struct.pack(">I",len(data)) + tag + data + struct.pack(">I",zlib.crc32(tag+data)&0xffffffff)
        temporary = path.with_suffix(f".{os.getpid()}.tmp")
        temporary.write_bytes(b"\x89PNG\r\n\x1a\n" +
                              chunk(b"IHDR",struct.pack(">IIBBBBB",w,h,8,2,0,0,0)) +
                              chunk(b"IDAT",zlib.compress(raw,9)) + chunk(b"IEND",b""))
        temporary.replace(path)
    image = bpy.data.images.load(str(path), check_existing=True)
    image.colorspace_settings.name = "sRGB" if srgb else "Non-Color"
    image.pack()
    return image


def textures(kind, tone, rough, metal=0):
    n = 1024 if kind in ("wood", "cloth", "rug", "patchwork", "wallpaper", "plaster") else 512
    y, x = np.mgrid[0:n, 0:n] / n
    rng = np.random.default_rng(61219)
    noise = rng.normal(0, 1, (n, n))
    height = np.zeros_like(x)
    shade = np.zeros_like(x)
    cavity = np.ones_like(x)
    base = np.broadcast_to(color(tone), (n, n, 3)).copy()
    def noise_field(cells):
        # Periodic value noise with visible low-frequency material variation.
        values=rng.uniform(-1,1,(cells,cells))
        u=x*cells;v=y*cells;ix=u.astype(int);iy=v.astype(int)
        fx=u-ix;fy=v-iy;fx=fx*fx*(3-2*fx);fy=fy*fy*(3-2*fy)
        return ((values[iy,ix]*(1-fx)+values[iy,(ix+1)%cells]*fx)*(1-fy)+
                (values[(iy+1)%cells,ix]*(1-fx)+values[(iy+1)%cells,(ix+1)%cells]*fx)*fy)
    if kind == "wood":
        bend=y+.028*np.sin(x*math.tau)+.011*np.sin(x*math.tau*2+.9)
        rings=np.sin(bend*math.tau*10+1.4*np.sin(x*math.tau))
        grain=np.sin(bend*math.tau*62+.7*np.sin(x*math.tau*3))
        pores=np.maximum(0,np.sin(bend*math.tau*211+.5*np.sin(x*19)))**18
        cathedral=np.sin((y+.12*np.sin(x*math.pi)**2)*math.tau*17)
        flecks=(np.maximum(0,np.sin(x*math.tau*29+y*15))**24 *
                np.maximum(0,np.sin(y*math.tau*83))**18)
        shade=.028*rings+.014*grain+.012*cathedral-.026*pores+.003*noise+.018*noise_field(5)
        base += shade[:,:,None]*np.array([1.0,.87,.65])
        base += flecks[:,:,None]*.037
        shade=np.zeros_like(x)
        height=.11*grain-.12*pores+.025*rings
        cavity=1-.20*pores
        roughness=rough+.095*rings+.035*grain-.04*flecks
    elif kind in ("cloth", "rug", "patchwork"):
        threads = 120 if kind == "rug" else 86
        warp=np.cos(x*math.tau*threads)
        weft=np.cos(y*math.tau*threads)
        cross=((x*threads).astype(int)+(y*threads).astype(int))%2
        weave=np.where(cross==0,warp*.75+weft*.25,weft*.75+warp*.25)
        yarn=noise_field(16)
        shade=.025*weave+.006*yarn+.003*noise
        height=.19*weave
        cavity=1-.19*np.maximum(0,-weave)
        roughness=rough+.035*weave+.025*yarn
        if kind == "rug":
            # Handwoven kilim: terracotta field, cream nested diamonds, slate
            # and olive medallions, mustard border, fine interrupted stitch.
            base[:] = color("AE604B")
            edge = np.minimum.reduce((x, 1 - x, y, 1 - y))
            base[edge < .063] = color("C39C55")
            base[(edge > .022) & (edge < .040)] = color("67794E")
            base[(edge > .068) & (edge < .083)] = color("E4D3AE")
            dx = np.abs(((x * 3 + .0) % 1) - .5)
            dy = np.abs(((y * 2) % 1) - .5)
            diamond = dx + dy
            inner = edge > .105
            base[inner & (diamond < .37)] = color("E6D4AE")
            base[inner & (diamond < .27)] = color("5F7C88")
            base[inner & (diamond < .18)] = color("C19743")
            base[inner & (diamond < .10)] = color("794C3D")
            stitch = (((x * 72) % 1) < .48) & (edge > .047) & (edge < .053)
            base[stitch] = color("EEDCC0")
        elif kind == "patchwork":
            colors = [color(PALETTE[k]) for k in ("mustard", "slate", "brick", "olive", "linen", "lavender")]
            a = (x * 6).astype(int)
            b = (y * 6).astype(int)
            for i, c in enumerate(colors):
                mask = ((a + 2 * b) % 6) == i
                base[mask] = c
            u, v = (x * 6) % 1, (y * 6) % 1
            seams = (np.minimum(u, 1 - u) < .018) | (np.minimum(v, 1 - v) < .018)
            base[seams] = color("DCC9A9")
            height[seams] -= .05
            dots = ((np.abs(u - .5) + np.abs(v - .5)) < .20) & (((a + b) % 2) == 0)
            base[dots] = color("E8D8B7")
    elif kind == "wallpaper":
        # Reference: plain, deep olive mineral paint with broad brush mottling.
        shade=.013*noise_field(5)+.007*noise_field(22)+.0015*noise
        height=.018*noise_field(40)
        roughness=rough+.035*noise_field(12)
    elif kind == "plaster":
        shade=.014*noise_field(6)+.006*noise_field(26)+.0015*noise
        height=.018*noise_field(45)
        roughness=rough+.025*noise_field(16)
    elif kind == "metal":
        brush=np.sin(y*math.tau*230+.14*np.sin(x*18))
        shade=.009*brush+.025*noise_field(9)
        height=.005*brush
        roughness=rough+.045*noise_field(18)+.02*brush
    elif kind == "glaze":
        cloudy=noise_field(6)
        shade=.016*cloudy+.003*noise
        height=.016*noise_field(20)
        roughness=rough+.055*cloudy
    else:
        shade = .003 * noise + .004 * np.sin(x * math.tau * 3) * np.sin(y * math.tau * 4)
        height = .012 * noise if kind in ("plaster", "paper", "clay") else .003 * noise
        roughness = rough + .012 * noise
        if kind == "paper":
            shade += .003 * np.sin(y * math.tau * 91)
    base += shade[:, :, None]
    gy, gx = np.gradient(height)
    strength = .006 if kind in ("cloth", "rug", "patchwork") else .003
    normal = np.stack((-gx * n * strength, -gy * n * strength, np.ones_like(x)), axis=-1)
    normal /= np.linalg.norm(normal, axis=-1, keepdims=True)
    orm = np.stack((cavity, np.clip(roughness, .05, 1), np.full_like(x, metal)), axis=-1)
    key = f"{kind}-{tone}-{int(rough * 100)}-{int(metal * 100)}-reference-v2"
    return png(key + "-base", base, True), png(key + "-normal", normal * .5 + .5), png(key + "-orm", orm)


def pbr(key, kind, tone, rough=.6, metal=0):
    if key in _materials:
        return _materials[key]
    m = bpy.data.materials.new("Atelier | " + key)
    m.use_nodes = True
    tree = m.node_tree
    bs = tree.nodes.get("Principled BSDF")
    bs.inputs["Base Color"].default_value = (1, 1, 1, 1)
    bs.inputs["Roughness"].default_value = rough
    bs.inputs["Metallic"].default_value = metal
    if kind in ("cloth", "rug", "patchwork"):
        bs.inputs["Sheen Weight"].default_value = .14
        bs.inputs["Sheen Tint"].default_value = (*linear(color(tone)),1)
        bs.inputs["Sheen Roughness"].default_value = .7
    if kind == "glaze":
        bs.inputs["Coat Weight"].default_value = .22
        bs.inputs["Coat Roughness"].default_value = .24
    images = textures(kind, tone, rough, metal)
    nodes = []
    for img in images:
        node = tree.nodes.new("ShaderNodeTexImage")
        node.image = img
        nodes.append(node)
    tree.links.new(nodes[0].outputs["Color"], bs.inputs["Base Color"])
    nm = tree.nodes.new("ShaderNodeNormalMap")
    nm.inputs["Strength"].default_value = .10 if kind=="wood" else .75 if kind=="cloth" else .25
    tree.links.new(nodes[1].outputs["Color"], nm.inputs["Color"])
    tree.links.new(nm.outputs["Normal"], bs.inputs["Normal"])
    split = tree.nodes.new("ShaderNodeSeparateColor")
    tree.links.new(nodes[2].outputs["Color"], split.inputs["Color"])
    tree.links.new(split.outputs["Green"], bs.inputs["Roughness"])
    tree.links.new(split.outputs["Blue"], bs.inputs["Metallic"])
    # glTF exporter-recognized ambient occlusion socket, using the same ORM PNG.
    group = bpy.data.node_groups.get("glTF Material Output")
    if not group:
        group = bpy.data.node_groups.new("glTF Material Output", "ShaderNodeTree")
        group.interface.new_socket(name="Occlusion", in_out="INPUT", socket_type="NodeSocketFloat")
    ao = tree.nodes.new("ShaderNodeGroup")
    ao.node_tree = group
    tree.links.new(split.outputs["Red"], ao.inputs["Occlusion"])
    # The same baked geometric contact darkening is used by Cycles and GLB.
    vertex=tree.nodes.new("ShaderNodeVertexColor");vertex.layer_name="SurfaceAO"
    multiply=tree.nodes.new("ShaderNodeMixRGB");multiply.blend_type="MULTIPLY"
    multiply.inputs[0].default_value=1
    tree.links.new(nodes[0].outputs["Color"],multiply.inputs[1])
    tree.links.new(vertex.outputs["Color"],multiply.inputs[2])
    tree.links.new(multiply.outputs[0],bs.inputs["Base Color"])
    _materials[key] = m
    return m


def root_name(o):
    while o.parent:
        o = o.parent
    return o.name


def project(o, tile=.20, fit=False, world=False):
    """Physical-scale box projection, with grain following the longest axis."""
    layer = o.data.uv_layers.active or o.data.uv_layers.new(name="UVMap")
    coords = np.array([v.co[:] for v in o.data.vertices])
    lo, hi = coords.min(axis=0), coords.max(axis=0)
    span = np.maximum(hi - lo, .001)
    o.data.update()
    seed=int(hashlib.md5(o.name.encode()).hexdigest()[:8],16)
    offset=np.random.default_rng(seed).random(2)*3 if not fit and not world else (0,0)
    for face in o.data.polygons:
        normal_axis = max(range(3), key=lambda k: abs(face.normal[k]))
        axes = [k for k in range(3) if k != normal_axis]
        if not world: axes.sort(key=lambda k: span[k], reverse=True)
        for loop in face.loop_indices:
            v = o.data.vertices[o.data.loops[loop].vertex_index].co
            if world: v=o.matrix_world@v
            layer.data[loop].uv = tuple((v[k] - (0 if world else lo[k])) / (span[k] if fit else tile)+offset[a] for a,k in enumerate(axes))


def choose(old, o):
    name = old.name.lower()
    root = root_name(o)
    obj = o.name.lower()
    if name.startswith("atelier |"):
        return old
    if "walnut" in name or "end grain" in name:
        return pbr("Walnut", "wood", PALETTE["walnut"], .52)
    if "grain" in name or "oak" in name or "honey wood" in name or "turned honey" in name:
        return pbr("Honey oak", "wood", PALETTE["oak"], .52)
    if any(k in name for k in ("brass", "bronze", "gold")) and "bristle" not in name:
        return pbr("Satin aged brass", "metal", PALETTE["brass"], .36, .80)
    if any(k in name for k in ("iron", "charcoal", "chamber")):
        return pbr("Warm charcoal iron", "metal", PALETTE["iron"], .57, .28)
    if "rubber" in name:
        return pbr("Soft olive rubber", "plain", "3B423A", .86)
    if "seam cord" in name:
        tone=PALETTE["slate"] if root=="sofa" else PALETTE["mustard"] if root=="chair" else PALETTE["olive"]
        return pbr("Tailored welt "+root,"cloth",tone,.84)
    if any(k in name for k in ("linen", "boucle", "wool", "woven", "cushion", "cloth")):
        if root == "rug":
            return pbr("Kilim geometry", "rug", PALETTE["brick"], .92)
        if root == "sofa":
            return pbr("Slate upholstery", "cloth", PALETTE["slate"], .87)
        if root == "blanket":
            return pbr("Hand sewn patchwork", "patchwork", PALETTE["linen"], .91)
        if root == "chair":
            return pbr("Mustard upholstery", "cloth", PALETTE["mustard"], .88)
        if root=="ottoman":
            return pbr("Olive upholstery","cloth",PALETTE["olive"],.88)
        if root in ("cushion", "woodfish"):
            return pbr("Brick textile", "cloth", PALETTE["brick"], .88)
        if root in ("entryway", "book") or "journal" in root:
            return pbr("Olive wool", "cloth", PALETTE["olive"], .86)
        return pbr("Natural shade linen", "cloth", PALETTE["linen"], .86)
    if "wallpaper" in name:
        return pbr("Olive mineral paint", "wallpaper", PALETTE["olive"], .86)
    if "plaster" in name:
        return pbr("Warm mineral plaster", "plaster", PALETTE["cream"], .90)
    if "joinery" in name:
        return pbr("Ivory painted joinery", "plain", "E4DDCE", .47)
    if "carton " in name and "category" in name:
        tone = "7B8452" if "home" in name else "BB8E42" if "light" in name else "B06B58"
        return pbr("Box category " + tone, "paper", tone, .91)
    if "carton" in name or "kraft" in name:
        return pbr("Kraft fibre", "paper", "BFA177", .94)
    if "paper" in name or "packing tape" in name or "rice dough" in name:
        return pbr("Cotton paper", "paper", "EADABD", .92)
    if ("glaze" in name and "unglazed" not in name) or "porcelain" in name or "ceramic" in name:
        tone = "637F91" if "blue" in name else "88A18A" if "celadon" in name else "E9DBC3"
        return pbr("Ceramic " + tone, "glaze", tone, .25)
    if "terracotta" in name and "cushion" not in name or "brick" in name or "roof" in name:
        if "roof" in name:
            tone = ("AA5942", "BE7250", "96513F")[int(name[-1])] if name[-1].isdigit() else "AA5942"
        elif "brick" in name:
            tone = ("A75C43", "B76B4C", "C17B56", "99563F")[int(name[-1])] if name[-1].isdigit() else "AE674B"
        else:
            tone = "B17450"
        return pbr("Fired clay " + tone, "clay", tone, .79)
    if "enamel" in name:
        return pbr("Garden enamel" if "garden" in name else "Mustard cleaning enamel",
                   "glaze", "657C53" if "garden" in name else "C1A154", .37)
    if "bristle" in name:
        return pbr("Natural brush fibres", "wood", "C4A56B", .90)
    if "mortar" in name:
        return pbr("Lime mortar", "plaster", "B19C7D", .95)
    if "printed forest ink" in name:
        return pbr("Olive print", "plain", "4B5D3C", .92)
    if "leaf" in name or "leaves" in name or "jade" in name:
        return pbr("Botanical " + old.name, "plain",
                   "506B3D" if "young" not in name else "8F9D58", .64)
    if "candy red" in name or "red cups" in name:
        return pbr("Brick lacquer", "glaze", "AC5140", .32)
    if "rose" in name:
        return pbr("Warm rose", "plain", "C08375", .67)
    if "mint jelly" in name:
        return pbr("Jade toy", "glaze", "789878", .38)
    if "bubble " in name:
        tones = [PALETTE[k] for k in ("brick", "mustard", "linen", "olive", "slate", "lavender")]
        return pbr("Toy color " + name[-1], "plain", tones[int(name[-1])], .67)
    if name == "warm star":
        return pbr("Honey star", "glaze", "D4AA50", .36, .1)
    return old


def apply():
    bpy.context.view_layer.update()
    for o in list(bpy.data.objects):
        if o.type != "MESH":
            continue
        kinds = set()
        for slot in o.material_slots:
            if not slot.material:
                continue
            slot.material = choose(slot.material, o)
            kinds.add(slot.material.name)
        if not kinds or o.name.startswith("Herringbone oak"):
            continue
        if any(k in " ".join(kinds) for k in ("mineral","plaster")):
            project(o,tile=.60,world=True)
        elif any(k in " ".join(kinds) for k in ("Kilim", "patchwork")):
            project(o, fit=True)
        elif any("Atelier |" in k for k in kinds):
            # Preserve wrapping on round ceramic surfaces; fix generated cube,
            # cloth and wooden UVs before their transforms are merged.
            if any(k in " ".join(kinds) for k in ("oak", "Walnut", "upholstery", "textile", "wallpaper", "Kraft", "paper", "linen", "fibres")):
                project(o, tile=.48 if "wood" in " ".join(kinds).lower() or "oak" in " ".join(kinds) or "Walnut" in " ".join(kinds)
                        else .075 if any(k in " ".join(kinds) for k in ("upholstery","textile","welt","linen")) else .30)
            elif not o.data.uv_layers:
                project(o,tile=.60 if any(k in " ".join(kinds) for k in ("mineral","plaster")) else .20)
    bake_occlusion()
    print("ATELIER_PBR_APPLIED", len(_materials), flush=True)


def parquet(parent, bounds, y=-.324):
    """Clipped, 45-degree herringbone with real joints and no new root nodes."""
    mats = [pbr("Parquet " + str(i), "wood", tone, .49+(i%3)*.018)
            for i, tone in enumerate(("A47B52","A9845D","97714B","B18B5F","8D6949","9F7955","B08A61","A27950"))]
    xmin, xmax, zmin, zmax = bounds
    w, m, gap = .043, 5, .00065
    cs = math.sqrt(.5)

    def clip(poly, axis, bound, lower):
        out = []
        for a, b in zip(poly, poly[1:] + poly[:1]):
            ina = a[axis] >= bound if lower else a[axis] <= bound
            inb = b[axis] >= bound if lower else b[axis] <= bound
            if ina:
                out.append(a)
            if ina != inb:
                t = (bound - a[axis]) / (b[axis] - a[axis])
                out.append(tuple(a[k] + t * (b[k] - a[k]) for k in range(2)))
        return out

    for i in range(-18, 19):
        for j in range(-80, 81):
            u, v = (i * m - j) * w, (i * m + j) * w
            for index, (a, b, du, dv) in enumerate(((u, v, m*w, w), (u+(m-1)*w, v+w, w, m*w))):
                raw = [(a+gap/2, b+gap/2), (a+du-gap/2, b+gap/2),
                       (a+du-gap/2, b+dv-gap/2), (a+gap/2, b+dv-gap/2)]
                poly = [(cs*(x-z), cs*(x+z)) for x, z in raw]
                for axis, bound, lower in ((0,xmin,True),(0,xmax,False),(1,zmin,True),(1,zmax,False)):
                    if poly:
                        poly = clip(poly, axis, bound, lower)
                if len(poly) < 3:
                    continue
                n = len(poly)
                data = bpy.data.meshes.new("Herringbone plank")
                verts = [(x, -z, y+.004) for x,z in poly] + [(x,-z,y-.004) for x,z in poly]
                faces = [tuple(range(n-1,-1,-1)), tuple(range(n,n*2))]
                faces += [(k,(k+1)%n,(k+1)%n+n,k+n) for k in range(n)]
                data.from_pydata(verts, [], faces)
                data.update()
                o = bpy.data.objects.new("Herringbone oak", data)
                bpy.context.collection.objects.link(o)
                o.parent = parent
                data.materials.append(mats[(i*7+j*3+index) % len(mats)])
                layer = data.uv_layers.new(name="UVMap")
                for face in data.polygons:
                    for loop in face.loop_indices:
                        p = data.vertices[data.loops[loop].vertex_index].co
                        xx, zz = cs*(p.x-p.y), cs*(-p.y-p.x)
                        offset=((i*13+j*17)%31)/7
                        layer.data[loop].uv = ((xx-a)/.36+offset, (zz-b)/.24+offset) if index == 0 else ((zz-b)/.36+offset, (xx-a)/.24+offset)
                # Narrow chamfers catch window light; the plank joints remain real.
                bevel=o.modifiers.new("Parquet edge light","BEVEL")
                bevel.width=.00045;bevel.segments=1
                bpy.context.view_layer.objects.active=o
                bpy.ops.object.modifier_apply(modifier=bevel.name)


def bake_occlusion():
    """Local surface contact shading per movable root, exported as COLOR_0.

    Roots overlap at origin during asset authoring. Build a separate BVH per
    root, so no neighbouring prototype can contaminate another item's shading.
    """
    bpy.context.view_layer.update()
    groups={}
    for o in bpy.data.objects:
        if o.type=="MESH": groups.setdefault(root_name(o),[]).append(o)
    directions=[Vector((math.cos(i*2.39996)*math.sqrt(1-z*z),
                        math.sin(i*2.39996)*math.sqrt(1-z*z),z))
                for i in range(10) for z in [(i+.5)/10]]
    for name,meshes in groups.items():
        verts=[];faces=[]
        for o in meshes:
            offset=len(verts)
            verts.extend(o.matrix_world@v.co for v in o.data.vertices)
            faces.extend(tuple(offset+i for i in f.vertices) for f in o.data.polygons)
        if not faces: continue
        tree=BVHTree.FromPolygons(verts,faces,all_triangles=False,epsilon=0)
        reach=.045 if name!="renovation_house" else .075
        for o in meshes:
            data=o.data
            attr=data.color_attributes.get("SurfaceAO") or data.color_attributes.new(
                name="SurfaceAO",type="FLOAT_COLOR",domain="POINT")
            data.color_attributes.active_color=attr
            nm=o.matrix_world.to_3x3().inverted().transposed()
            cache={}
            for v in data.vertices:
                normal=(nm@v.normal).normalized()
                if normal.length<.5: normal=Vector((0,0,1))
                position=o.matrix_world@v.co
                key=tuple(round(c,4) for c in (*position,*normal))
                if key in cache: value=cache[key]
                else:
                    basis=normal.to_track_quat("Z","Y")
                    start=position+normal*.00018
                    occluded=0
                    for ray in directions:
                        hit,_,_,distance=tree.ray_cast(start,basis@ray,reach)
                        if hit is not None: occluded+=(1-distance/reach)**.7
                    value=max(.52,1-.70*occluded/len(directions))
                    cache[key]=value
                attr.data[v.index].color=(value,value,value,1)
        print("SURFACE_AO",name,len(verts),flush=True)


def warm_preview(scene):
    scene.view_settings.view_transform = "AgX"
    scene.view_settings.look = "AgX - Medium High Contrast"
    scene.cycles.use_denoising = True
    scene.cycles.samples = 64
    if scene.world:
        scene.world.use_nodes = True
        bg = scene.world.node_tree.nodes.get("Background")
        bg.inputs[0].default_value = (.70, .76, .82, 1)
        bg.inputs[1].default_value = .27
    lights = [o for o in bpy.data.objects if o.type == "LIGHT" and o.data.type == "AREA"]
    for i, light in enumerate(lights):
        light.data.color = (1, .88, .70) if i == 0 else (.80, .89, 1)
    scene.render.image_settings.file_format = "PNG"


# Builders also exec their shared helper prefix; use this import rather than
# module-level side effects, so art tuning runs only on completed geometry.
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))
