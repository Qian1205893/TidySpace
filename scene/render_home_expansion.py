"""Inspect the shipped GLB, not the authoring scene, in a labelled studio sheet."""
import bpy
import json
import sys
from pathlib import Path
from mathutils import Vector

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/"play-home-expansion-20261115"
bpy.ops.object.select_all(action="SELECT");bpy.ops.object.delete(use_global=False)
bpy.ops.import_scene.gltf(filepath=str(ROOT/"app/src/main/assets/models/tidy-home-expansion.glb"))
items=list(json.loads((ROOT/"scene/home-expansion/manifest.json").read_text()))
toy_only="--toys" in sys.argv
if toy_only: items=items[-6:]
cols=3 if toy_only else 6
rows=(len(items)+cols-1)//cols
for o in list(bpy.data.objects):
    if o.parent is None and o.name not in items:
        o.hide_render=True
        for c in o.children_recursive: c.hide_render=True
ink=bpy.data.materials.new("Catalogue ink");ink.diffuse_color=(.095,.079,.056,1)
for index,name in enumerate(items):
    root=bpy.data.objects[name]
    corners=[root.matrix_world.inverted()@o.matrix_world@Vector(c) for o in root.children_recursive
             if o.type=="MESH" for c in o.bound_box]
    lo=Vector(tuple(min(p[k] for p in corners) for k in range(3)))
    hi=Vector(tuple(max(p[k] for p in corners) for k in range(3)))
    factor=.30/max(hi-lo)
    root.scale=(factor,)*3
    x=(index%cols-(cols-1)/2)*.48;y=-(index//cols)*.49
    root.location=(x-(lo.x+hi.x)*factor/2,y-(lo.y+hi.y)*factor/2,-lo.z*factor)
    curve=bpy.data.curves.new(name,"FONT");curve.body=f"{index+1:02} / {name.upper()}"
    curve.align_x="CENTER";curve.size=.017
    label=bpy.data.objects.new(name+" label",curve);bpy.context.collection.objects.link(label)
    label.location=(x,y-.218,.001);curve.materials.append(ink)
floor=bpy.data.materials.new("Warm gallery paper");floor.use_nodes=True
floor.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value=(.56,.52,.45,1)
floor.node_tree.nodes["Principled BSDF"].inputs["Roughness"].default_value=.9
bpy.ops.mesh.primitive_plane_add(size=200,location=(0,0,-.001));bpy.context.object.data.materials.append(floor)
scene=bpy.context.scene;scene.world.use_nodes=True
scene.world.node_tree.nodes["Background"].inputs[0].default_value=(.70,.77,.82,1)
scene.world.node_tree.nodes["Background"].inputs[1].default_value=.35
target=Vector((0,-(rows-1)*.245,0))
for name,p,energy,size,color in [
    ("Window",(-2,1,4),420,3,(1,.89,.74)),("Fill",(2,-1,3),150,3,(.81,.91,1))]:
    data=bpy.data.lights.new(name,"AREA");data.energy=energy;data.size=size;data.color=color
    obj=bpy.data.objects.new(name,data);bpy.context.collection.objects.link(obj);obj.location=p
    obj.rotation_euler=(target-obj.location).to_track_quat("-Z","Y").to_euler()
camera=bpy.data.objects.new("Camera",bpy.data.cameras.new("Camera"))
bpy.context.collection.objects.link(camera);camera.location=target+Vector((0,-3.2,5.8))
camera.rotation_euler=(target-camera.location).to_track_quat("-Z","Y").to_euler()
camera.data.type="ORTHO";camera.data.ortho_scale=1.60 if toy_only else 3.13;scene.camera=camera
scene.render.engine="CYCLES";scene.cycles.samples=32;scene.cycles.use_denoising=True
scene.view_settings.view_transform="AgX";scene.view_settings.look="AgX - Medium High Contrast"
scene.render.resolution_x=2100 if toy_only else 3000
scene.render.resolution_y=1550 if toy_only else 2600
scene.render.resolution_percentage=100
scene.render.filepath=str(OUT/("six-handheld-toys.png" if toy_only else "thirty-new-models.png"))
bpy.ops.render.render(write_still=True)
