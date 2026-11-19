"""Original tabletop instruments, PICO metres/Y-up; run with Blender --background."""
import bpy
import math
import json
import wave
import struct
import sys
from pathlib import Path
from mathutils import Vector

HERE = Path(__file__).resolve().parent
sys.path.insert(0,str(HERE))
import art_direction as atelier
OUT = HERE / "toys"
ASSETS = HERE.parent / "app/src/main/assets"
OUT.mkdir(exist_ok=True)
(ASSETS / "sounds").mkdir(exist_ok=True)
bpy.ops.object.select_all(action="SELECT")
bpy.ops.object.delete(use_global=False)

def xyz(p):
    return (p[0], -p[2], p[1])

def mat(name, color, rough=.4, metal=0, texture=None):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    bs = m.node_tree.nodes.get("Principled BSDF")
    bs.inputs["Base Color"].default_value = (*color, 1)
    bs.inputs["Roughness"].default_value = rough
    bs.inputs["Metallic"].default_value = metal
    if texture:
        for suffix, slot in (("base", "Base Color"), ("rough", "Roughness"), ("normal", "Normal")):
            img = bpy.data.images.load(str(HERE / "furniture" / f"{texture}_{suffix}.png"), check_existing=True)
            if suffix != "base":
                img.colorspace_settings.name = "Non-Color"
            img.pack()
            node = m.node_tree.nodes.new("ShaderNodeTexImage")
            node.image = img
            if suffix == "normal":
                normal = m.node_tree.nodes.new("ShaderNodeNormalMap")
                normal.inputs["Strength"].default_value = .35
                m.node_tree.links.new(node.outputs["Color"], normal.inputs["Color"])
                m.node_tree.links.new(normal.outputs["Normal"], bs.inputs[slot])
            else:
                m.node_tree.links.new(node.outputs["Color"], bs.inputs[slot])
    return m

wood = mat("Honey wood grain", (.48, .24, .08), texture="oak")
dark = mat("Polished walnut", (.14, .054, .020), .3)
gold = mat("Brushed bell bronze", (.52, .29, .10), .3, .78)
linen = mat("Terracotta woven cushion", (.44, .19, .10), .83, texture="cushion")
black = mat("Sound chamber", (.013, .007, .004), .9)
ivory = mat("Porcelain songbird", (.84, .80, .66), .22)

def empty(name, parent=None, p=(0, 0, 0)):
    o = bpy.data.objects.new(name, None)
    bpy.context.collection.objects.link(o)
    o.parent = parent
    o.location = xyz(p)
    return o

def finish(o, name, parent, material):
    o.name, o.parent = name, parent
    o.data.materials.append(material)
    for face in o.data.polygons:
        face.use_smooth = True
    return o

def box(name, parent, size, p, material, bevel=.002):
    bpy.ops.mesh.primitive_cube_add(size=1, location=xyz(p))
    o = bpy.context.object
    o.dimensions = (size[0], size[2], size[1])
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    mod = o.modifiers.new("Crafted rounded edge", "BEVEL")
    mod.width, mod.segments = min(bevel, min(size)*.4), 3
    bpy.ops.object.modifier_apply(modifier=mod.name)
    return finish(o, name, parent, material)

def ball(name, parent, size, p, material):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=40, ring_count=20, radius=1, location=xyz(p))
    o = bpy.context.object
    o.scale = (size[0]/2, size[2]/2, size[1]/2)
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    return finish(o, name, parent, material)

def lathe(name, parent, profile, material, p=(0, 0, 0)):
    verts, faces = [], []
    n = 64
    for r, y in profile:
        for i in range(n+1):
            a = i*math.tau/n
            verts.append(xyz((p[0]+r*math.cos(a), p[1]+y, p[2]+r*math.sin(a))))
    for j in range(len(profile)-1):
        for i in range(n):
            a = j*(n+1)+i
            faces.append((a, a+n+1, a+n+2, a+1))
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata(verts, [], faces)
    mesh.update()
    o = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(o)
    uv = mesh.uv_layers.new()
    for poly in mesh.polygons:
        for loop in poly.loop_indices:
            index = mesh.loops[loop].vertex_index
            uv.data[loop].uv = ((index % (n+1))/n, (index//(n+1))/max(1, len(profile)-1))
    return finish(o, name, parent, material)

def rod(name, parent, a, b, radius, material):
    delta = Vector(xyz(b))-Vector(xyz(a))
    bpy.ops.mesh.primitive_cylinder_add(vertices=24, radius=radius, depth=delta.length,
        location=(Vector(xyz(a))+Vector(xyz(b)))/2)
    o = bpy.context.object
    o.rotation_mode = "QUATERNION"
    o.rotation_quaternion = delta.to_track_quat("Z", "Y")
    return finish(o, name, parent, material)

roots = {name: empty(name) for name in ("woodfish", "musicbox", "bowl")}
r = roots["woodfish"]
ball("Woven meditation pad", r, (.15, .018, .12), (0, -.041, 0), linen)
body = ball("Carved wooden fish", r, (.122, .073, .104), (0, .004, 0), wood)
# Carve a genuine acoustic slit through the curved front.
cutter = box("Slot cutter", None, (.104, .0045, .049), (0, .009, .042), black, .0018)
bpy.context.view_layer.objects.active = body
mod = body.modifiers.new("Acoustic opening", "BOOLEAN")
mod.operation, mod.object = "DIFFERENCE", cutter
bpy.ops.object.modifier_apply(modifier=mod.name)
bpy.data.objects.remove(cutter, do_unlink=True)
box("Recess inside acoustic slit", r, (.097, .003, .003), (0, .009, .019), black, .001)
for x in (-.035, .035):
    ball("Carved eye ring", r, (.013, .013, .008), (x, .025, .040), dark)
    ball("Brass eye inlay", r, (.005, .005, .002), (x, .026, .044), gold)
for i in range(24):
    a=i*math.tau/24
    ball("Sewn pad detail", r, (.002, .002, .002), (.068*math.cos(a), -.038, .052*math.sin(a)), gold)
hammer = empty("woodfish_hammer", r, (.052, .031, .002))
rod("Mallet handle", hammer, (0, 0, 0), (0, .004, .043), .0024, dark)
ball("Mallet head", hammer, (.023, .018, .020), (0, .006, .043), wood)

r = roots["musicbox"]
box("Walnut music cabinet", r, (.136, .065, .11), (0, -.021, 0), dark, .006)
for y in (-.05, .011):
    box("Fine brass edge inlay", r, (.138, .002, .112), (0, y, 0), gold, .001)
box("Oak lid inset", r, (.126, .004, .1), (0, .014, 0), wood, .003)
for x in (-.052, .052):
    for z in (-.036, .036):
        ball("Cabinet foot", r, (.015, .009, .015), (x, -.053, z), gold)
box("Front keyhole", r, (.010, .016, .002), (0, -.019, .0555), gold, .004)
rotor = empty("musicbox_rotor", r, (0, .017, 0))
lathe("Turntable", rotor, [(0,0),(.041,0),(.043,.003),(.041,.006),(0,.006)], gold)
ball("Songbird body", rotor, (.040, .021, .021), (0, .021, 0), ivory)
ball("Songbird head", rotor, (.018, .018, .018), (.016, .032, 0), ivory)
ball("Carved wing", rotor, (.026, .012, .009), (-.002, .024, .010), wood)
rod("Little beak", rotor, (.022, .032, 0), (.033, .031, 0), .0024, gold)
for x in (-.005, .009):
    rod("Songbird foot", rotor, (x, .006, 0), (x, .016, 0), .0012, gold)
crank = empty("musicbox_crank", r, (.069, -.01, 0))
rod("Crank axle", crank, (0,0,0),(.010,0,0),.0024,gold)
rod("Crank arm", crank, (.010,0,0),(.010,-.018,0),.0022,gold)
rod("Crank grip", crank, (.009,-.018,0),(.019,-.018,0),.004,wood)

r = roots["bowl"]
lathe("Resting ring", r, [(0,-.035),(.030,-.035),(.034,-.032),(.032,-.027),(0,-.027)], linen)
lathe("Hand hammered singing bowl", r, [
    (0,-.028),(.031,-.028),(.044,-.021),(.055,-.005),(.060,.018),
    (.059,.032),(.057,.035),(.054,.033),(.055,.018),(.049,-.002),(.038,-.017),(0,-.021)
], gold)
for y, radius in ((.021,.0595),(.028,.0588)):
    lathe("Engraved rim", r, [(radius-.0006,y-.0006),(radius,y),(radius-.0006,y+.0006)], dark)
striker = empty("bowl_striker", r, (.044, .023, .022))
rod("Felt striker shaft", striker, (-.026,.009,0),(.014,.004,0),.0034,dark)
ball("Soft striker head", striker, (.018,.012,.014),(-.028,.009,0),linen)

atelier.apply()
for parent in list(roots.values())+[hammer, rotor, crank, striker]:
    children=[o for o in parent.children if o.type=="MESH"]
    if not children:
        continue
    bpy.ops.object.select_all(action="DESELECT")
    for o in children:
        o.select_set(True)
    bpy.context.view_layer.objects.active=children[0]
    if len(children)>1:
        bpy.ops.object.join()
    o=children[0]
    o.name=parent.name+"_mesh"
    bpy.ops.object.transform_apply(location=False,rotation=True,scale=True)
    mod=o.modifiers.new("Export triangles","TRIANGULATE")
    bpy.ops.object.modifier_apply(modifier=mod.name)

bpy.context.view_layer.update()
manifest={}
for name, root in roots.items():
    coords=[root.matrix_world.inverted()@child.matrix_world@Vector(c)
        for child in root.children_recursive if child.type=="MESH" for c in child.bound_box]
    pico=[(c.x,c.z,-c.y) for c in coords]
    manifest[name]={"min":[min(p[k] for p in pico) for k in range(3)],"max":[max(p[k] for p in pico) for k in range(3)]}
(OUT/"manifest.json").write_text(json.dumps(manifest,indent=2))
bpy.ops.object.select_all(action="SELECT")
bpy.ops.export_scene.gltf(filepath=str(ASSETS/"models/tidy-toys.glb"),export_format="GLB",
    export_yup=True,export_apply=True,export_tangents=True,export_animations=False)
if "--export-only" in sys.argv:
    print("TIDY_TOYS_EXPORT_COMPLETE",flush=True)
    sys.exit(0)

# Short, original modal-synthesis sounds; no network audio and no endless loops.
rate=24000
def sound(name, duration, partials):
    with wave.open(str(ASSETS/"sounds"/f"{name}.wav"),"wb") as f:
        f.setparams((1,2,rate,0,"NONE","not compressed"))
        samples=[]
        for n in range(int(rate*duration)):
            t=n/rate
            attack=min(1,t/.002)
            value=sum(amp*math.sin(math.tau*hz*t)*math.exp(-decay*t) for hz,amp,decay in partials)
            samples.append(struct.pack("<h",int(max(-.92,min(.92,value*attack)) *32767)))
        f.writeframes(b"".join(samples))
sound("woodfish",.36,[(780,.48,25),(1280,.27,40),(2170,.14,65)])
sound("bowl",2.4,[(440,.22,1.9),(881,.13,2.5),(1203,.065,3.8),(1742,.045,4.7)])
for index,hz in enumerate((523.25,659.25,783.99,1046.5,880,783.99,659.25,587.33)):
    sound(f"music{index}",.5,[(hz,.26,8),(hz*2,.10,17),(hz*3,.055,22)])

for name,x in (("woodfish",-.19),("musicbox",0),("bowl",.20)):
    roots[name].location=xyz((x,.0575 if name=="musicbox" else .05 if name=="woodfish" else .035,0))
floor=box("Preview tabletop only",None,(.7,.02,.35),(0,-.011,0),wood,.005)
world=bpy.context.scene.world
world.use_nodes=True
world.node_tree.nodes["Background"].inputs[0].default_value=(.6,.65,.72,1)
world.node_tree.nodes["Background"].inputs[1].default_value=.4
for p,power,size in [((-.3,.6,.3),35,.5),((.4,.3,-.2),20,.4)]:
    light=bpy.data.lights.new("Studio softbox","AREA")
    light.energy,light.size=power,size
    obj=bpy.data.objects.new("Studio softbox",light)
    bpy.context.collection.objects.link(obj)
    obj.location=xyz(p)
    obj.rotation_euler=(Vector(xyz((0,.03,0)))-obj.location).to_track_quat("-Z","Y").to_euler()
camera=bpy.data.objects.new("Inspection",bpy.data.cameras.new("Inspection"))
bpy.context.collection.objects.link(camera)
camera.location=xyz((.34,.39,.68))
camera.rotation_euler=(Vector(xyz((0,.04,0)))-camera.location).to_track_quat("-Z","Y").to_euler()
camera.data.type,camera.data.ortho_scale="ORTHO",.73
scene=bpy.context.scene
scene.camera=camera
scene.render.engine="CYCLES"
scene.cycles.samples=40
scene.cycles.use_denoising=True
scene.render.resolution_x,scene.render.resolution_y,scene.render.resolution_percentage=1600,900,100
scene.view_settings.view_transform="AgX"
scene.render.filepath=str(OUT/"inspection.png")
atelier.warm_preview(scene)
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/"TidySpace-toys.blend"))
bpy.ops.render.render(write_still=True)
print("TIDY_TOYS_COMPLETE",flush=True)
