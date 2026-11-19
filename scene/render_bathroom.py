"""Render the shipped annex and fixtures, not planning illustrations."""
import bpy
import json
import math
from pathlib import Path
from mathutils import Vector

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT.parent/"TidySpace-bathroom-expansion-20261115"
bpy.ops.object.select_all(action="SELECT")
bpy.ops.object.delete(use_global=False)
def xyz(p): return Vector((p[0],-p[2],p[1]))
for family in ("renovation","moving-home","furniture","home-expansion","bathroom"):
    bpy.ops.import_scene.gltf(filepath=str(ROOT/"app/src/main/assets/models"/f"tidy-{family}.glb"))
objects={o.name:o for o in bpy.data.objects}
def hide(o):
    o.hide_render=True
    for c in o.children_recursive: c.hide_render=True
for o in list(bpy.data.objects):
    if o.parent is None and o.name not in ("renovation_house","bathroom_house"):
        hide(o)
def place(name,p,yaw=0):
    o=objects[name]
    o.hide_render=False
    for c in o.children_recursive: c.hide_render=False
    o.location=xyz(p)
    o.rotation_euler.z=math.radians(-yaw)
for name,p,yaw in [
    ("bathtub",(-1.19,-.22,1.985),0),
    ("toilet",(-.34,-.205,1.505),90),
    ("bathvanity",(-1.36,-.17,1.49),90),
    ("washer",(-1.36,-.175,1.125),90),
    ("laundrybasket",(-1.105,-.225,1.13),0),
    ("mirror",(-1.52515,.16,1.49),90),
    ("desk",(.40,-.15,-.90),0),
    ("sofa",(-.3,-.185,-.85),0),
    ("chair",(.42,-.175,-.48),25),
    ("coffee",(-.30,-.245,-.40),0),
    ("lamp",(-.78,-.05,-.80),0),
    ("rug",(-.25,-.316,-.62),0),
]:
    place(name,p,yaw)
# Render a furnishing showcase; do not write it into the user's save.
for name in ("dust-0","dust-1","wall-0","wall-1"):
    if name in objects: hide(objects[name])
scene=bpy.context.scene
scene.world.use_nodes=True
scene.world.node_tree.nodes["Background"].inputs[0].default_value=(.62,.70,.77,1)
scene.world.node_tree.nodes["Background"].inputs[1].default_value=.5
for name,p,energy,size,color in [
    ("Warm daylight",(-2.5,4,1.0),440,4,(1,.90,.76)),
    ("Soft sky",(3,3,2),230,4,(.80,.90,1)),
    ("Bath front fill",(0,3,4),150,3,(1,.95,.88)),
]:
    data=bpy.data.lights.new(name,"AREA")
    data.energy=energy;data.size=size;data.color=color
    o=bpy.data.objects.new(name,data);bpy.context.collection.objects.link(o)
    o.location=xyz(p)
    o.rotation_euler=(xyz((-.7,-.1,1.3))-o.location).to_track_quat("-Z","Y").to_euler()
camera=bpy.data.objects.new("Camera",bpy.data.cameras.new("Camera"))
bpy.context.collection.objects.link(camera);scene.camera=camera
camera.data.type="ORTHO"
scene.render.engine="CYCLES";scene.cycles.samples=32;scene.cycles.use_denoising=True
scene.view_settings.view_transform="AgX"
scene.view_settings.look="AgX - Medium High Contrast"
scene.render.resolution_x=2200;scene.render.resolution_y=1800;scene.render.resolution_percentage=100
def render(name,p,target,scale):
    camera.location=xyz(p)
    camera.rotation_euler=(xyz(target)-camera.location).to_track_quat("-Z","Y").to_euler()
    camera.data.ortho_scale=scale
    scene.render.filepath=str(OUT/name)
    bpy.ops.render.render(write_still=True)
render("bathroom-detail.png",(2.4,2.8,4.8),(-.82,.0,1.61),2.17)
render("house-with-bathroom.png",(4.5,5.5,7),(.20,.02,.55),5.25)
