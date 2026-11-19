"""Offline art inspection of the exact shipped GLBs. Never writes app saves.

The composition below is a staged asset preview, not an emulator screenshot.
All model materials and dimensions come from the exported native-runtime files.
"""
import bpy
import json
import math
import os
from pathlib import Path
from mathutils import Vector

HERE = Path(__file__).resolve().parent
OUT = HERE.parent / os.environ.get("TIDY_RENDER_OUT","visual-refresh-20261115")
OUT.mkdir(exist_ok=True)
bpy.ops.object.select_all(action="SELECT")
bpy.ops.object.delete(use_global=False)

def xyz(p): return (p[0], -p[2], p[1])
def hide(o, value=True):
    o.hide_render=value
    for child in o.children_recursive: child.hide_render=value

for family in ("renovation","furniture","moving-home","toys","playthings"):
    bpy.ops.import_scene.gltf(filepath=str(HERE.parent/"app/src/main/assets/models"/f"tidy-{family}.glb"))

objects = {o.name: o for o in bpy.data.objects}
for o in list(objects.values()):
    if o.parent: continue
    if o.name.startswith(("clean_","dust-","debris-","wall-","trash-")): hide(o)

bounds = {}
for family in ("furniture","moving-home","toys","playthings"):
    data=json.loads((HERE/family/"manifest.json").read_text())
    bounds.update(data.get("items",data))
def place(name,x,z,y=None,yaw=0):
    o=objects[name]
    if y is None: y=-.32-bounds[name]["min"][1]
    o.location=xyz((x,y,z))
    o.rotation_euler.z=math.radians(-yaw)
    return o

place("desk",-.70,-1.08)
place("chair",-.73,-.74)
place("sofa",.10,-1.02)
place("coffee",.10,-.63)
place("rug",.10,-.73)
place("lamp",-.23,-.97)
place("shelf",.65,-1.365,.20)
place("picture",.16,-1.367,.31)
place("plant",-.87,-1.09,.103)
place("mug",-.58,-1.025,.055)
place("book",.05,-.62,-.159)
place("cushion",.16,-1.00,-.19)
place("sidetable",-.16,-.52)
place("tablelamp",-.16,-.52,-.01)
place("ottoman",.48,-.54)
place("blanket",.48,-.53,-.23)
place("crate",.62,-.10)
objects["crate_lid"].rotation_euler.x=math.radians(-58)
place("shoecabinet",1.26,1.07)
place("coatrack",1.22,1.40)
place("gardenbench",1.87,-.77,yaw=90)
place("wallclock",.72,-1.365,.44)
for i,key in enumerate(("furniture","light","home","toys")):
    place("movingbox-"+key,.50+i*.27,1.34)
# Instruments have enough clearance on the desk and coffee table.
place("woodfish",-.75,-1.085,.055)
place("musicbox",-.58,-1.12,.075)
place("bowl",.16,-.64,-.13)
for i,name in enumerate(("rattle","pokeball","mochi","jelly","popboard","spinner","tumbler","ratchet")):
    place(name,-.62+(i%4)*.19,.0+(i//4)*.19)

floor=bpy.data.materials.new("Offline display background")
floor.diffuse_color=(.32,.26,.18,1)
floor.use_nodes=True
floor.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value=(.32,.26,.18,1)
floor.node_tree.nodes["Principled BSDF"].inputs["Roughness"].default_value=.92
bpy.ops.mesh.primitive_plane_add(size=200,location=(0,0,-.41))
bpy.context.object.data.materials.append(floor)

scene=bpy.context.scene
scene.render.engine="CYCLES"
scene.cycles.samples=48
scene.cycles.use_denoising=True
scene.view_settings.view_transform="AgX"
scene.view_settings.look="AgX - Medium High Contrast"
scene.view_settings.exposure=-.25
scene.world.use_nodes=True
bg=scene.world.node_tree.nodes["Background"]
bg.inputs[0].default_value=(.70,.77,.85,1)
bg.inputs[1].default_value=.18
def aim(o,target): o.rotation_euler=(Vector(xyz(target))-o.location).to_track_quat("-Z","Y").to_euler()
def area(name,p,power,size,c,target):
    data=bpy.data.lights.new(name,"AREA")
    data.energy=power;data.size=size;data.color=c
    o=bpy.data.objects.new(name,data);bpy.context.collection.objects.link(o)
    o.location=xyz(p);aim(o,target)
area("Window daylight",(-2.4,2.5,-2.3),230,1.0,(1,.91,.75),(-.4,-.2,-.3))
area("Quiet room fill",(2.7,2.8,2.5),65,3.0,(.79,.89,1),(.2,0,0))
area("Interior warm bounce",(.05,.95,-.64),18,.85,(1,.89,.72),(.0,-.2,-.74))
sun=bpy.data.objects.new("Late morning sun",bpy.data.lights.new("Late morning sun","SUN"))
bpy.context.collection.objects.link(sun)
sun.data.energy=1.7;sun.data.angle=.065;sun.data.color=(1,.87,.68)
sun.location=xyz((-3,4,-2.4));aim(sun,(.4,-.32,.6))
camera=bpy.data.objects.new("Art inspection",bpy.data.cameras.new("Art inspection"))
bpy.context.collection.objects.link(camera)
scene.camera=camera;camera.data.type="ORTHO"
scene.render.resolution_percentage=100
scene.render.image_settings.file_format="PNG"
for name,p,target,scale,w,h in (
    ("room-art-preview",(4.0,3.6,5.2),(.3,.10,.16),5.2,2200,1600),
    ("materials-detail",(1.35,1.35,1.10),(-.15,-.02,-.81),1.78,1800,1400),
):
    camera.location=xyz(p);aim(camera,target);camera.data.ortho_scale=scale
    scene.render.resolution_x=w;scene.render.resolution_y=h
    scene.render.filepath=str(OUT/(name+".png"))
    if name=="room-art-preview": bpy.ops.wm.save_as_mainfile(filepath=str(OUT/"Art-inspection.blend"))
    bpy.ops.render.render(write_still=True)
