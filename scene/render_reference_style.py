"""Render only shipped GLBs, using a reference-matched editorial composition."""
import bpy
import math
import json
from pathlib import Path
from mathutils import Vector

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/"reference-style-20261115"
OUT.mkdir(exist_ok=True)
bpy.ops.object.select_all(action="SELECT")
bpy.ops.object.delete(use_global=False)
def xyz(p): return Vector((p[0],-p[2],p[1]))
def hide(o):
    o.hide_render=True
    for child in o.children_recursive:child.hide_render=True
for family in ("renovation","furniture","moving-home","toys","playthings"):
    bpy.ops.import_scene.gltf(filepath=str(ROOT/"app/src/main/assets/models"/f"tidy-{family}.glb"))
objects={o.name:o for o in bpy.data.objects}
for o in objects.values():
    if not o.parent and o.name.startswith(("clean_","dust-","debris-","wall-","trash-")):hide(o)
manifest={}
for family in ("furniture","moving-home","toys","playthings"):
    data=json.loads((ROOT/"scene"/family/"manifest.json").read_text())
    manifest.update(data.get("items",data))
def place(name,x,z,y=None,yaw=0):
    o=objects[name]
    if y is None:y=-.32-manifest[name]["min"][1]
    o.location=xyz((x,y,z));o.rotation_euler.z=math.radians(-yaw)
    return o

# Room and every object retain their production scale. This arrangement is an
# offline showcase, never a replacement for the user's saved furnishing layout.
place("sofa",-.39,-.60,yaw=-8)
place("chair",-.89,-.54,yaw=28)
place("coffee",-.45,-.21)
place("sidetable",-1.19,-.67)
place("tablelamp",-1.19,-.67,-.010)
place("desk",.39,-1.05)
place("shelf",.70,-1.365,.24)
place("picture",.17,-1.367,.38)
place("lamp",-.08,-.80)
place("rug",-.41,-.52)
place("plant",.25,-1.10,.10)
place("mug",.49,-1.02,.055)
place("book",-.49,-.20,-.159,yaw=13)
place("cushion",-.37,-.58,-.19,yaw=-8)
place("ottoman",-.08,-.16)
place("blanket",-.07,-.16,-.15,yaw=12)
place("woodfish",.25,-1.055,.055)
place("musicbox",.48,-1.10,.075)
place("bowl",-.37,-.20,-.13)
place("shoecabinet",1.25,1.20,yaw=180)
place("coatrack",1.20,1.48)
place("gardenbench",1.86,-.78,yaw=90)
place("wallclock",.70,-1.365,.49)
place("crate",-.91,.21)
objects["crate_lid"].rotation_euler.x=math.radians(-65)
for i,key in enumerate(("furniture","light","home","toys")):
    place("movingbox-"+key,.48+i*.26,1.36)
for i,name in enumerate(("rattle","pokeball","mochi","jelly","popboard","spinner","tumbler","ratchet")):
    place(name,-.62+(i%4)*.18,.12+(i//4)*.22)

world=bpy.context.scene.world;world.use_nodes=True
world.node_tree.nodes["Background"].inputs[0].default_value=(.58,.66,.73,1)
world.node_tree.nodes["Background"].inputs[1].default_value=.48
def point_light(name,p,energy,color,size):
    data=bpy.data.lights.new(name,"AREA");data.energy=energy;data.color=color;data.shape="DISK";data.size=size
    o=bpy.data.objects.new(name,data);bpy.context.collection.objects.link(o);o.location=xyz(p)
    o.rotation_euler=(xyz((-.45,-.08,-.55))-o.location).to_track_quat("-Z","Y").to_euler()
point_light("Window daylight",(-3.3,2.2,-2.7),520,(1,.90,.74),.42)
point_light("Interior reflected daylight",(.3,1.6,2.2),76,(.88,.92,1),3.5)
data=bpy.data.lights.new("Afternoon window sun","SUN");data.energy=2.0;data.angle=.045
sun=bpy.data.objects.new("Afternoon window sun",data);bpy.context.collection.objects.link(sun)
sun.location=xyz((-3.5,3,-4));sun.rotation_euler=(xyz((.1,-.3,.4))-sun.location).to_track_quat("-Z","Y").to_euler()
data.color=(1,.91,.77)
fire=bpy.data.lights.new("Warm live hearth","POINT");fire.energy=3;fire.color=(1,.32,.075);fire.shadow_soft_size=.04
obj=bpy.data.objects.new("Warm live hearth",fire);bpy.context.collection.objects.link(obj);obj.location=xyz((-1.36,-.16,.42))
backdrop=bpy.data.materials.new("Showcase background");backdrop.diffuse_color=(.16,.17,.09,1)
backdrop.use_nodes=True;backdrop.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value=(.16,.17,.09,1)
backdrop.node_tree.nodes["Principled BSDF"].inputs["Roughness"].default_value=1
bpy.ops.mesh.primitive_plane_add(size=200,location=(0,0,-.41));bpy.context.object.data.materials.append(backdrop)
scene=bpy.context.scene;scene.render.engine="CYCLES";scene.cycles.samples=64;scene.cycles.use_denoising=True
scene.view_settings.view_transform="AgX";scene.view_settings.look="AgX - Medium High Contrast"
scene.view_settings.exposure=-.10
scene.render.image_settings.file_format="PNG"
camera=bpy.data.objects.new("Reference composition",bpy.data.cameras.new("Reference composition"))
bpy.context.collection.objects.link(camera);scene.camera=camera;camera.data.type="ORTHO"
def render(name,p,target,scale,width,height):
    camera.location=xyz(p);camera.rotation_euler=(xyz(target)-camera.location).to_track_quat("-Z","Y").to_euler()
    camera.data.ortho_scale=scale;scene.render.resolution_x=width;scene.render.resolution_y=height
    scene.render.resolution_percentage=100;scene.render.filepath=str(OUT/(name+".png"))
    bpy.ops.render.render(write_still=True)
render("room-reference-style",(3.6,4.1,5),(.12,.06,.04),4.8,2400,1800)
render("furniture-material-closeup",(1.1,1.5,1.75),(-.48,-.03,-.55),1.67,2100,1600)
render("hearth-and-window",(.70,.9,1.30),(-1.1,.12,-.1),1.55,1600,1800)
print("REFERENCE_ART_PREVIEWS_COMPLETE",flush=True)
