"""Small, separate cleaning props and alpha atlases. Existing house GLBs stay intact."""
import bpy
import json
import math
import random
from pathlib import Path
from mathutils import Vector

HERE = Path(__file__).resolve().parent
PRIVATE = Path("/tmp/tidyspace-expanded-cleaning-20261116")
DELIVERY = HERE.parent.parent / "TidySpace-expanded-cleaning-20261116"
ASSETS = HERE.parent / "app/src/main/assets/models"
PRIVATE.mkdir(parents=True,exist_ok=True)
DELIVERY.mkdir(parents=True,exist_ok=True)
bpy.ops.object.select_all(action="SELECT")
bpy.ops.object.delete(use_global=False)
roots = {}
def xyz(p): return (p[0], -p[2], p[1])
def empty(name, parent=None, p=(0, 0, 0)):
    o = bpy.data.objects.new(name, None)
    bpy.context.collection.objects.link(o)
    o.parent = parent
    o.location = xyz(p)
    roots[name] = o
    return o
def material(name, color, rough=.65, metal=0):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    bs = m.node_tree.nodes.get("Principled BSDF")
    rgb = [int(color[i:i+2], 16)/255 for i in (0, 2, 4)]
    bs.inputs["Base Color"].default_value = tuple(
        v/12.92 if v <= .04045 else ((v+.055)/1.055)**2.4 for v in rgb)+(1,)
    bs.inputs["Roughness"].default_value = rough
    bs.inputs["Metallic"].default_value = metal
    return m
sage = material("Satin sage enamel", "708575", .31, .24)
cream = material("Ivory rolled enamel", "E5DDC9", .34, .15)
steel = material("Brushed warm steel", "A1A59D", .32, .8)
rubber = material("Rubber pedal and base", "303933", .86)
liner = material("Dark bin liner", "202C25", .8)
wood = material("Honey oak handle", "A68156", .63)
cloth = material("Ivory cotton mop", "D5CFB8", .94)
paper = material("Loose paper scraps", "D7CCAF", .91)
leaf = material("Autumn dry leaves", "A4844A", .9)
bottle = material("Discarded tea bottle", "746B45", .4)
red = material("Faded can paint", "A6654B", .48, .45)
foil = material("Crumpled snack foil", "BE9B64", .47, .25)
def finish(o, name, parent, mat):
    o.name = name
    o.parent = parent
    o.data.materials.append(mat)
    return o
def box(name, parent, size, p, mat, bevel=.002):
    bpy.ops.mesh.primitive_cube_add(size=1, location=xyz(p))
    o = bpy.context.object
    o.dimensions = (size[0], size[2], size[1])
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    if bevel:
        mod = o.modifiers.new("Soft manufactured edge", "BEVEL")
        mod.width = min(bevel, min(size)*.25)
        mod.segments = 2
        bpy.ops.object.modifier_apply(modifier=mod.name)
    return finish(o, name, parent, mat)
def cone(name, parent, r1, r2, height, p, mat, n=32):
    bpy.ops.mesh.primitive_cone_add(vertices=n, radius1=r1, radius2=r2,
        depth=height, location=xyz(p))
    return finish(bpy.context.object, name, parent, mat)
def ring(name, parent, radius, minor, p, mat):
    bpy.ops.mesh.primitive_torus_add(major_radius=radius, minor_radius=minor,
        major_segments=32, minor_segments=6, location=xyz(p))
    return finish(bpy.context.object, name, parent, mat)
def shell(name, parent, bottom, top, y0, y1, thickness, mat):
    verts = []
    for r, y in [(bottom, y0), (top, y1), (top-thickness, y1),
                 (bottom-thickness, y0+.009)]:
        verts += [xyz((r*math.cos(i*math.tau/40), y, r*math.sin(i*math.tau/40))) for i in range(40)]
    faces = []
    for band in range(3):
        faces += [(band*40+i, band*40+(i+1)%40, (band+1)*40+(i+1)%40, (band+1)*40+i) for i in range(40)]
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata(verts, [], faces)
    mesh.update()
    o = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(o)
    for f in mesh.polygons: f.use_smooth = True
    return finish(o, name, parent, mat)

# Root is the centre of the opening. Bottom rests exactly on the -0.32m floor.
bin_root = empty("clean_bin", p=(1.276, -.025, .609))
shell("Rolled tapered enamel body", bin_root, .105, .124, -.282, -.004, .006, sage)
shell("Visible dark inner liner", bin_root, .096, .115, -.264, -.012, .002, liner)
cone("Deep closed liner bottom", bin_root, .097, .097, .007, (0, -.266, 0), liner)
cone("Raised rubber base", bin_root, .108, .108, .013, (0, -.2885, 0), rubber)
ring("Thick ivory rolled rim", bin_root, .120, .006, (0, -.005, 0), cream)
for x in (-.126, .126):
    box("Carry handle", bin_root, (.013, .052, .041), (x, -.095, 0), steel, .004)
box("Pedal lever", bin_root, (.058, .012, .073), (0, -.271, .109), steel, .004)
box("Textured rubber pedal", bin_root, (.064, .012, .037), (0, -.259, .137), rubber, .003)
for n in range(4):
    box("Pedal grip rib", bin_root, (.052, .002, .002), (0, -.252, .124+n*.008), steel, .0005)
lid = empty("bin_lid", bin_root, (0, .008, -.122))
cone("Circular hinged lid", lid, .127, .127, .009, (0, 0, .116), sage)
ring("Lid ivory lip", lid, .123, .003, (0, -.005, .116), cream)
box("Lid carry tab", lid, (.046, .016, .024), (0, .013, .190), cream, .005)
lid.rotation_euler.x = math.radians(-105)
halo = empty("bin_mouth_halo", bin_root)
ring("Subtle mouth light", halo, .137, .002, (0, .003, 0), material("Bin capture mint", "A3CABB", .45))

mop = empty("clean_Mop", p=(.18, -.302, .69))
head = empty("mop_head", mop)
box("Mop cotton pad", head, (.176, .018, .080), (0, -.007, 0), cloth, .006)
box("Mop enamel plate", head, (.160, .017, .068), (0, .008, 0), sage, .004)
for n in range(12):
    box("Cotton seams", head, (.006, .004, .074), (-.075+n*.0135, -.015, 0), cream, .001)
cone("Mop swivel", mop, .015, .010, .032, (0, .03, 0), steel, 16)
cone("Oak mop shaft", mop, .007, .007, .335, (0, .212, 0), wood, 16)
cone("Mop soft grip", mop, .010, .010, .052, (0, .364, 0), rubber, 16)
stand = empty("clean_stand_extra")
box("Mop drip tray", stand, (.21, .009, .112), (.18, -.3155, .69), sage, .004)

catalog = json.loads((HERE/"cleaning_expansion.json").read_text())
footprints = {}
def smooth(v):
    v = max(0, min(1, v))
    return v*v*(3-2*v)
def stain(m):
    name, size = m["id"], (m["size"][0], m["size"][2])
    seed = sum(ord(c) for c in name)*37
    phase = random.Random(seed).uniform(0, math.tau)
    def alpha(u, v):
        x, z = u*2-1, v*2-1
        angle = math.atan2(z, x)
        if name == "spill-1":
            # Three recognisable pairs of shoe marks; surrounding padding is transparent.
            value = 0
            for cx, cz in [(-.33, -.61), (.27, -.23), (-.30, .38)]:
                r = math.hypot((x-cx)/.21, (z-cz)/.36)
                value = max(value, smooth((1-r)*8)*(.57 if (int((z-cz)*50)%5) else .25))
            return value
        edge = .80+.075*math.sin(3*angle+phase)+.045*math.cos(7*angle)
        fade = smooth((1-math.hypot(x, z)/edge)*6)
        noise = .57+.13*math.sin(u*71+phase)*math.cos(v*57)+.08*math.sin(u*137+v*53)
        return min(.85, fade*noise*(1.1 if name.startswith("spill") else .86))
    root = empty(name, p=m["position"])
    # 24 feathered patches share one 256x192 atlas. No duplicated textures per material.
    pixels = [0.0]*(256*192*4)
    weights = []
    patches = []
    tone = (.20, .11, .045) if name.startswith("spill") else (.26, .22, .16)
    for row in range(3):
        for col in range(8):
            w, h = size[0]/8, size[1]/3
            lo, hi = max(-size[0]/2, (col-.12)*w-size[0]/2), min(size[0]/2, (col+1.12)*w-size[0]/2)
            bottom, top = max(-size[1]/2, (row-.12)*h-size[1]/2), min(size[1]/2, (row+1.12)*h-size[1]/2)
            alphas = []
            for iy in range(64):
                z = bottom+(top-bottom)*(iy+.5)/64
                for ix in range(32):
                    x = lo+(hi-lo)*(ix+.5)/32
                    mask = (1 if col==0 else smooth((x-lo)/(.24*w)))*(1 if col==7 else smooth((hi-x)/(.24*w)))*(
                        1 if row==0 else smooth((z-bottom)/(.24*h)))*(1 if row==2 else smooth((top-z)/(.24*h)))
                    a = 1-(1-alpha(x/size[0]+.5, z/size[1]+.5))**mask
                    offset = ((row*64+iy)*256+col*32+ix)*4
                    pixels[offset:offset+4] = (*tone, a)
                    alphas.append(a)
            weights.append(round(sum(alphas)/len(alphas)*1_000_000))
            patches.append((row, col, lo, hi, bottom, top))
    image = bpy.data.images.new(name+" feathered atlas", width=256, height=192, alpha=True)
    image.pixels.foreach_set(pixels)
    image.filepath_raw = str(PRIVATE/(name+"-atlas.png"))
    image.file_format = "PNG"
    image.save()
    image.pack()
    for row, col, lo, hi, bottom, top in patches:
        mat = material(f"{name} cell {row*8+col} translucent surface dirt", "FFFFFF", .78 if name.startswith("spill") else 1)
        mat.surface_render_method = "BLENDED"
        mat.use_backface_culling = False
        bs = mat.node_tree.nodes.get("Principled BSDF")
        tex = mat.node_tree.nodes.new("ShaderNodeTexImage")
        tex.image = image
        mat.node_tree.links.new(tex.outputs["Color"], bs.inputs["Base Color"])
        mat.node_tree.links.new(tex.outputs["Alpha"], bs.inputs["Alpha"])
        pts = [(lo,bottom),(hi,bottom),(hi,top),(lo,top)]
        mesh = bpy.data.meshes.new(name+" stain")
        mesh.from_pydata([xyz((x, .00045+(row*8+col)*.00001, z)) for x,z in pts], [], [(3,2,1,0)])
        mesh.update()
        uv = mesh.uv_layers.new(name="UVMap")
        for loop in mesh.loops:
            x,z = pts[loop.vertex_index]
            uv.data[loop.index].uv = ((col+(x-lo)/(hi-lo))/8, (row+(z-bottom)/(top-bottom))/3)
        o = bpy.data.objects.new(name+" stain", mesh)
        bpy.context.collection.objects.link(o)
        finish(o, name+" stain", root, mat)
    footprints[name] = weights

for m in catalog:
    name = m["id"]
    if m["kind"] in ("Dust", "Spill"):
        stain(m)
        continue
    root = empty(name, p=m["position"])
    rng = random.Random(name)
    if m["kind"] == "Debris":
        bands = [empty(f"{name}_wipe-{i}", root) for i in range(8)]
        for i in range(12):
            x, z = rng.uniform(-.44,.44)*m["size"][0], rng.uniform(-.44,.44)*m["size"][2]
            piece = empty(f"{name}_piece-{i}", bands[min(7,max(0,int((x/m["size"][0]+.5)*8)))], (x,0,z))
            if name == "debris-4":
                mesh = bpy.data.meshes.new("Curled autumn leaf")
                mesh.from_pydata([xyz(p) for p in [(-.021,-.007,0),(0,-.005,-.010),(.024,-.006,0),(0,-.004,.010),(0,.004,0)]],
                    [], [(0,1,4),(1,2,4),(2,3,4),(3,0,4)])
                mesh.update()
                o = bpy.data.objects.new("Curled autumn leaf",mesh)
                bpy.context.collection.objects.link(o)
                finish(o,o.name,piece,leaf)
            else:
                o = box("Folded paper scrap", piece, (.018+i%3*.008,.003,.014), (0,-.006,0), paper, .0006)
                o.rotation_euler.z = rng.uniform(-1,1)
    elif name == "trash-3":
        cone("Drink bottle",root,.031,.031,.084,(0,-.016,0),bottle,24)
        cone("Bottle shoulder",root,.031,.013,.019,(0,.035,0),bottle,24)
        cone("Bottle cap",root,.014,.014,.017,(0,.051,0),cream,24)
        shell("Paper bottle label",root,.0315,.0315,-.026,.003,.0005,paper)
    elif name in ("trash-4","trash-6"):
        cone("Empty drinks can",root,.033,.033,.09,(0,0,0),red,24)
        cone("Visible can top",root,.032,.032,.003,(0,.046,0),steel,24)
        ring("Can rolled lip",root,.031,.002,(0,.047,0),steel)
        box("Can pull tab",root,(.018,.002,.011),(0,.049,0),rubber,.002)
    else:
        bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=2,radius=1)
        o = bpy.context.object
        for v in o.data.vertices:
            v.co *= rng.uniform(.76,1.0)
        o.scale = (.056,.042,.033)
        bpy.ops.object.transform_apply(location=False,rotation=False,scale=True)
        finish(o,"Crumpled snack wrapper",root,foil)
        for x in (-.05,.05): box("Wrapper folded seam",root,(.012,.035,.064),(x,0,0),paper,.002)

for parent in list(roots.values())[::-1]:
    meshes = [o for o in parent.children if o.type=="MESH"]
    if not meshes: continue
    bpy.ops.object.select_all(action="DESELECT")
    for o in meshes: o.select_set(True)
    bpy.context.view_layer.objects.active = meshes[0]
    if len(meshes)>1: bpy.ops.object.join()
    o = meshes[0]
    o.name = parent.name+"_mesh"
    bpy.ops.object.transform_apply(location=False,rotation=True,scale=True)
    mod = o.modifiers.new("Export triangles","TRIANGULATE")
    bpy.ops.object.modifier_apply(modifier=mod.name)
bpy.context.view_layer.update()
manifest = {}
for name, root in roots.items():
    coords = [root.matrix_world.inverted()@o.matrix_world@Vector(c)
              for o in root.children_recursive if o.type=="MESH" for c in o.bound_box]
    if not coords: continue
    pico = [(p.x,p.z,-p.y) for p in coords]
    manifest[name] = {"min":[min(p[k] for p in pico) for k in range(3)],
                     "max":[max(p[k] for p in pico) for k in range(3)]}
(PRIVATE/"expanded-manifest.json").write_text(json.dumps({"bounds":manifest,"footprints":footprints},indent=2))
halo.hide_render = True
for o in halo.children_recursive: o.hide_render = True
bpy.ops.object.select_all(action="SELECT")
bpy.ops.export_scene.gltf(filepath=str(ASSETS/"tidy-cleaning-expanded.glb"),export_format="GLB",
    export_yup=True,export_apply=True,export_tangents=True,export_animations=False)
print("EXPANDED_CLEANING_EXPORTED",flush=True)

# Preview the actual exported geometry, then the unchanged expanded house with new dirt.
world = bpy.context.scene.world
world.use_nodes = True
world.node_tree.nodes["Background"].inputs[0].default_value = (.55,.59,.56,1)
world.node_tree.nodes["Background"].inputs[1].default_value = .45
for p,power in [((0,2.5,1.4),130),((-1,1.2,-1),75)]:
    light = bpy.data.lights.new("Softbox","AREA")
    light.energy = power
    light.size = 2
    o = bpy.data.objects.new("Softbox",light)
    bpy.context.collection.objects.link(o)
    o.location = xyz(p)
    o.rotation_euler = (-o.location).to_track_quat("-Z","Y").to_euler()
camera = bpy.data.objects.new("Inspection",bpy.data.cameras.new("Inspection"))
bpy.context.collection.objects.link(camera)
scene = bpy.context.scene
scene.camera = camera
scene.render.engine = "CYCLES"
scene.cycles.samples = 24
scene.cycles.use_denoising = True
scene.render.resolution_x = 1400
scene.render.resolution_y = 1000
scene.render.resolution_percentage = 100
scene.view_settings.view_transform = "AgX"
def view(target,p,scale,path):
    camera.location = xyz(p)
    camera.rotation_euler = (Vector(xyz(target))-camera.location).to_track_quat("-Z","Y").to_euler()
    camera.data.type = "ORTHO"
    camera.data.ortho_scale = scale
    scene.render.filepath = str(path)
    bpy.ops.render.render(write_still=True)
originals = {name:root.location.copy() for name,root in roots.items()}
for name,root in roots.items():
    if root.parent is not None: continue
    root.hide_render = name not in ("clean_bin","clean_Mop","trash-3","trash-4","trash-5")
    for child in root.children_recursive: child.hide_render = root.hide_render or child.parent==halo
bin_root.location = xyz((-.18,.295,0))
mop.location = xyz((.18,.018,0))
for name,p in [("trash-3",(-.26,.06,.22)),("trash-4",(-.09,.05,.22)),("trash-5",(.06,.04,.22))]:
    roots[name].location = xyz(p)
box("Studio plinth",None,(1.2,.03,.85),(0,-.025,0),material("Preview clay","D5C9B5"),.006)
view((0,.22,0),(.95,.85,1.45),.92,DELIVERY/"bin-mop-and-trash.png")
for o in list(bpy.data.objects):
    if o.name.startswith("Studio plinth"): bpy.data.objects.remove(o,do_unlink=True)
for name,root in roots.items():
    root.location = originals[name]
    root.hide_render = False
    for child in root.children_recursive: child.hide_render = False
halo.hide_render = True
for o in halo.children_recursive: o.hide_render = True
for filename in ("tidy-renovation.glb","tidy-study-room.glb","tidy-gaming-room.glb"):
    before = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=str(ASSETS/filename))
    for o in set(bpy.data.objects)-before:
        if o.name=="clean_bin.001" or (o.parent and o.parent.name=="clean_bin.001"):
            o.hide_render=True
view((.15,-.02,.7),(4.6,4.1,5.8),5.9,DELIVERY/"expanded-first-level.png")
# Avoid Blender's host-level thumbnail cache; the exported GLB is the source of preview evidence.
print("EXPANDED_CLEANING_PREVIEWS_COMPLETE",flush=True)
