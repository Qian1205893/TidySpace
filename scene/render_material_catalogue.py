"""Studio sheet of every shipped movable asset; sizes normalized for inspection."""
import bpy
import math
from pathlib import Path
from mathutils import Vector

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/"reference-style-20261115"
bpy.ops.object.select_all(action="SELECT");bpy.ops.object.delete(use_global=False)
for family in ("renovation","furniture","moving-home","toys","playthings"):
    bpy.ops.import_scene.gltf(filepath=str(ROOT/"app/src/main/assets/models"/f"tidy-{family}.glb"))
items=[
    ("sofa","01 / SLATE SOFA"),("chair","02 / MUSTARD CHAIR"),("desk","03 / OAK DESK"),
    ("coffee","04 / COFFEE TABLE"),("shelf","05 / WALL SHELF"),("lamp","06 / FLOOR LAMP"),
    ("shoecabinet","07 / WALNUT CABINET"),("coatrack","08 / COAT STAND"),("sidetable","09 / SIDE TABLE"),
    ("tablelamp","10 / CERAMIC LAMP"),("wallclock","11 / CLOCK"),("gardenbench","12 / GARDEN BENCH"),
    ("ottoman","13 / OLIVE OTTOMAN"),("blanket","14 / WOVEN BLANKET"),("rug","15 / KILIM RUG"),
    ("cushion","16 / TUFTED CUSHION"),("plant","17 / TERRACOTTA POT"),("picture","18 / FRAMED ART"),
    ("book","19 / CLOTH JOURNAL"),("mug","20 / GLAZED CUP"),("crate","21 / WOODEN CRATE"),
    ("woodfish","22 / WOODFISH"),("musicbox","23 / MUSIC BOX"),("bowl","24 / SINGING BOWL"),
    ("rattle","25 / RATTLE"),("pokeball","26 / LACQUER BALL"),("mochi","27 / SOFT MOCHI"),
    ("jelly","28 / JADE JELLY"),("popboard","29 / POP BOARD"),("spinner","30 / SPINNER"),
    ("tumbler","31 / CERAMIC TUMBLER"),("ratchet","32 / RATCHET"),("clean_Vacuum","33 / VACUUM"),
    ("clean_Broom","34 / WOODEN BROOM"),("clean_Brush","35 / WALL BRUSH"),("clean_bin","36 / ENAMEL BIN"),
    ("movingbox-furniture","37 / FURNITURE BOX"),("movingbox-light","38 / LIGHT BOX"),
    ("movingbox-home","39 / HOME BOX"),("movingbox-toys","40 / TOY BOX"),
    ("trash-0","41 / CARDBOARD"),("trash-1","42 / PAPER CUP"),("trash-2","43 / SCRAP PAPER"),
]
names={name for name,_ in items}
for o in bpy.data.objects["pokeball_star"].children_recursive:
    o.hide_render=True
for root in list(bpy.data.objects):
    if root.parent:continue
    if root.name not in names:
        root.hide_render=True
        for child in root.children_recursive:child.hide_render=True
ink=bpy.data.materials.new("Catalogue lettering");ink.diffuse_color=(.1,.085,.06,1)
ink.use_nodes=True;ink.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value=(.1,.085,.06,1)
for index,(name,label) in enumerate(items):
    root=bpy.data.objects[name];root.location=(0,0,0);root.rotation_euler=(0,0,0)
    bpy.context.view_layer.update()
    corners=[root.matrix_world.inverted()@o.matrix_world@Vector(v) for o in root.children_recursive
             if o.type=="MESH" for v in o.bound_box]
    lo=Vector(tuple(min(p[k] for p in corners) for k in range(3)))
    hi=Vector(tuple(max(p[k] for p in corners) for k in range(3)))
    factor=.29/max(hi-lo)
    root.scale=(factor,)*3
    x=(index%7-3)*.44;y=-(index//7)*.47
    root.location=(x-(lo.x+hi.x)*factor/2,y-(lo.y+hi.y)*factor/2,-lo.z*factor)
    curve=bpy.data.curves.new(label,"FONT");curve.body=label;curve.size=.014;curve.align_x="CENTER"
    obj=bpy.data.objects.new(label,curve);bpy.context.collection.objects.link(obj)
    obj.location=(x,y-.218,.0007);obj.data.materials.append(ink)
floor=bpy.data.materials.new("Warm studio paper");floor.use_nodes=True
floor.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value=(.52,.47,.37,1)
floor.node_tree.nodes["Principled BSDF"].inputs["Roughness"].default_value=.9
bpy.ops.mesh.primitive_plane_add(size=200,location=(0,0,-.001));bpy.context.object.data.materials.append(floor)
scene=bpy.context.scene;scene.world.use_nodes=True
scene.world.node_tree.nodes["Background"].inputs[0].default_value=(.65,.72,.8,1)
scene.world.node_tree.nodes["Background"].inputs[1].default_value=.30
for name,p,energy,size,color in [
    ("Large window",(-2,1,4),380,2.5,(1,.88,.73)),("Cool fill",(2,0,3),90,3,(.8,.9,1))]:
    data=bpy.data.lights.new(name,"AREA");data.energy=energy;data.size=size;data.color=color
    obj=bpy.data.objects.new(name,data);bpy.context.collection.objects.link(obj);obj.location=p
    obj.rotation_euler=(Vector((0,-1.5,0))-obj.location).to_track_quat("-Z","Y").to_euler()
camera=bpy.data.objects.new("Catalogue camera",bpy.data.cameras.new("Catalogue camera"))
bpy.context.collection.objects.link(camera);camera.location=(0,-4.8,6.5)
camera.rotation_euler=(Vector((0,-1.37,0))-camera.location).to_track_quat("-Z","Y").to_euler()
camera.data.type="ORTHO";camera.data.ortho_scale=3.35;scene.camera=camera
scene.render.engine="CYCLES";scene.cycles.samples=48;scene.cycles.use_denoising=True
scene.view_settings.view_transform="AgX";scene.view_settings.look="AgX - Medium High Contrast"
scene.render.resolution_x=2800;scene.render.resolution_y=2800;scene.render.resolution_percentage=100
scene.render.filepath=str(OUT/"all-models-materials.png")
bpy.ops.render.render(write_still=True)
