"""Render the shipped GLB at three cleaning states for material/path inspection."""
import bpy
import math
import sys
from pathlib import Path
from mathutils import Vector

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/"cleaning-play-refresh-20261115"
bpy.ops.object.select_all(action="SELECT");bpy.ops.object.delete(use_global=False)
bpy.ops.import_scene.gltf(filepath=str(ROOT/"app/src/main/assets/models/tidy-renovation.glb"))
def xyz(p): return (p[0],-p[2],p[1])
def hide(name):
    o=bpy.data.objects[name];o.hide_render=True
    for c in o.children_recursive: c.hide_render=True
scene=bpy.context.scene
scene.world.use_nodes=True
scene.world.node_tree.nodes["Background"].inputs[0].default_value=(.72,.77,.80,1)
scene.world.node_tree.nodes["Background"].inputs[1].default_value=.35
target=Vector(xyz((.10,.15,-.18)))
for p,power,size in [((1,2,1.6),260,3),((-1.4,1.3,-.5),130,2)]:
    data=bpy.data.lights.new("Softbox","AREA");data.energy=power;data.size=size
    o=bpy.data.objects.new("Softbox",data);bpy.context.collection.objects.link(o);o.location=xyz(p)
    o.rotation_euler=(target-o.location).to_track_quat("-Z","Y").to_euler()
camera=bpy.data.objects.new("Camera",bpy.data.cameras.new("Camera"))
bpy.context.collection.objects.link(camera);camera.location=xyz((3.1,2.5,4.4))
camera.rotation_euler=(target-camera.location).to_track_quat("-Z","Y").to_euler()
camera.data.type="ORTHO";camera.data.ortho_scale=4.9;scene.camera=camera
scene.render.engine="CYCLES";scene.cycles.samples=24;scene.cycles.use_denoising=True
scene.cycles.transparent_max_bounces=64
scene.view_settings.view_transform="AgX";scene.view_settings.look="AgX - Medium High Contrast"
scene.render.resolution_x=1600;scene.render.resolution_y=1200;scene.render.resolution_percentage=100
scene.render.image_settings.file_format="PNG"
def render(name):
    scene.render.filepath=str(OUT/name);bpy.ops.render.render(write_still=True)
detail="--detail" in sys.argv
if not detail: render("cleaning-dirty-room.png")
# Clear one horizontal path, preserving the rest of the continuous stain.
for name in ("dust-0","dust-1","wall-0","wall-1"):
    for material in bpy.data.objects[f"{name}_mesh"].data.materials:
        i=int(material.name.split(" cell ")[1].split()[0])
        opacity=0 if i in (10,11,12,13) else .67 if i in (9,14) else 1
        bs=material.node_tree.nodes["Principled BSDF"]
        link=bs.inputs["Alpha"].links[0]
        factor=material.node_tree.nodes.new("ShaderNodeMath")
        factor.operation="MULTIPLY";factor.inputs[1].default_value=opacity
        material.node_tree.links.new(link.from_socket,factor.inputs[0])
        material.node_tree.links.new(factor.outputs[0],bs.inputs["Alpha"])
for name in ("debris-0","debris-1"):
    for i in range(28):
        o=bpy.data.objects[f"{name}_piece-{i}"]
        a=i*2.399;r=.017+math.sqrt(i/28)*.026
        o.location=xyz((math.cos(a)*r,.007+i%4*.003,math.sin(a)*r))
bpy.data.objects["clean_Vacuum"].location=xyz((-.60,-.303,-.855))
bpy.data.objects["clean_Broom"].location=xyz((-.348,-.299,-.116))
bpy.data.objects["clean_Brush"].location=xyz((.80,.25,-1.359))
foam=bpy.data.materials.new("Clean foam");foam.use_nodes=True
foam.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value=(.86,.84,.75,1)
foam.node_tree.nodes["Principled BSDF"].inputs["Roughness"].default_value=.76
for i in range(8):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=12,ring_count=8,radius=1,
        location=xyz((.80+math.cos(i*2.399)*.058,.25+math.sin(i*2.399)*.044,-1.376)))
    o=bpy.context.object;o.name="Localized brush foam";o.scale=(.008,.003,.006)
    o.data.materials.append(foam)
if detail:
    target=Vector(xyz((.80,.25,-1.37)))
    camera.location=xyz((1.18,.55,-.15))
    camera.rotation_euler=(target-camera.location).to_track_quat("-Z","Y").to_euler()
    camera.data.ortho_scale=.83
    render("cleaning-wall-detail.png")
    target=Vector(xyz((-.348,-.29,-.116)))
    camera.location=xyz((.03,.28,.56))
    camera.rotation_euler=(target-camera.location).to_track_quat("-Z","Y").to_euler()
    camera.data.ortho_scale=.48
    render("cleaning-sweep-detail.png")
    sys.exit(0)
render("cleaning-path-foam-heaps.png")
for o in list(bpy.data.objects):
    if o.name=="Localized brush foam" or o.name.startswith("Localized brush foam."): o.hide_render=True
for name in ("dust-0","dust-1","wall-0","wall-1","debris-0","debris-1",
    "trash-0","trash-1","trash-2","clean_Vacuum","clean_Broom","clean_Brush","clean_bin","clean_stand"):
    hide(name)
render("cleaning-restored-room.png")
