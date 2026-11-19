"""Preview the shipped study and gaming models in their declared positions."""
import bpy,json,math,sys
from pathlib import Path
from mathutils import Vector
ROOT=Path(__file__).resolve().parents[1]
OUT=Path(sys.argv[sys.argv.index("--output")+1]) if "--output" in sys.argv else ROOT.parent/"TidySpace-study-renovation-20261116"
bpy.ops.object.select_all(action="SELECT");bpy.ops.object.delete(use_global=False)
def xyz(p):return Vector((p[0],-p[2],p[1]))
for family in ("renovation","moving-home","furniture","home-expansion","gaming","gaming-room","study","study-room"):
    bpy.ops.import_scene.gltf(filepath=str(ROOT/"app/src/main/assets/models"/f"tidy-{family}.glb"))
objects={o.name:o for o in bpy.data.objects}
def hide(o):
    o.hide_render=True
    for c in o.children_recursive:c.hide_render=True
for o in list(bpy.data.objects):
    if o.parent is None and o.name not in ("renovation_house","gaming_house","study_house"):hide(o)
def place(name,p,yaw=0):
    o=objects[name];o.hide_render=False
    for c in o.children_recursive:c.hide_render=False
    o.location=xyz(p);o.rotation_euler.z=math.radians(-yaw)
def equipment(path):
    return next(n["props"]["equipment"] for n in json.loads(path.read_text())["nodes"] if n["id"]=="scene")
facts=equipment(OUT/"design-spec.json")
for a in facts+equipment(ROOT.parent/"TidySpace-gaming-renovation-20261115/design-spec.json"):
    place(a["id"],a["position"],a.get("yaw",180 if a["id"]=="gamingchair" else 0))
for name,p,yaw in [
    ("desk",(.40,-.15,-.90),0),("sofa",(-.3,-.185,-.85),0),
    ("chair",(.42,-.175,-.48),25),("coffee",(-.30,-.245,-.40),0),
    ("lamp",(-.78,-.05,-.80),0),("rug",(-.25,-.316,-.62),0)]:
    place(name,p,yaw)
for name in ("entryway","dust-0","dust-1","wall-0","wall-1"):
    if name in objects:hide(objects[name])
scene=bpy.context.scene;scene.world.use_nodes=True
scene.world.node_tree.nodes["Background"].inputs[0].default_value=(.62,.70,.77,1)
scene.world.node_tree.nodes["Background"].inputs[1].default_value=.5
for name,p,power,size,color in [
    ("Warm window light",(-2.5,4,1),420,4,(1,.90,.76)),
    ("Soft sky",(3,3,2),230,4,(.80,.90,1)),
    ("Front fill",(0,3,4),150,3,(1,.95,.88))]:
    d=bpy.data.lights.new(name,"AREA");d.energy=power;d.size=size;d.color=color
    o=bpy.data.objects.new(name,d);bpy.context.collection.objects.link(o);o.location=xyz(p)
    o.rotation_euler=(xyz((-.7,-.1,1.3))-o.location).to_track_quat("-Z","Y").to_euler()
camera=bpy.data.objects.new("Camera",bpy.data.cameras.new("Camera"))
bpy.context.collection.objects.link(camera);scene.camera=camera;camera.data.type="ORTHO"
scene.render.engine="CYCLES";scene.cycles.samples=40;scene.cycles.use_denoising=True
scene.view_settings.view_transform="AgX";scene.view_settings.look="AgX - Medium High Contrast"
scene.render.resolution_x=2200;scene.render.resolution_y=1800;scene.render.resolution_percentage=100
def render(name,p,target,scale,fit_objects=None):
    camera.location=xyz(p);camera.rotation_euler=(xyz(target)-camera.location).to_track_quat("-Z","Y").to_euler()
    if fit_objects:
        rotation=camera.rotation_euler.to_matrix()
        points=[rotation.transposed()@(o.matrix_world@Vector(c)-camera.location)
                for o in fit_objects for c in o.bound_box]
        lo=Vector((min(p.x for p in points),min(p.y for p in points),0))
        hi=Vector((max(p.x for p in points),max(p.y for p in points),0))
        camera.location+=rotation@((lo+hi)/2)
        scale=max(hi.x-lo.x,(hi.y-lo.y)*scene.render.resolution_x/scene.render.resolution_y)*1.10
    camera.data.ortho_scale=scale;scene.render.filepath=str(OUT/name);bpy.ops.render.render(write_still=True)
if "--sheet-only" not in sys.argv:
    render("study-room-detail.png",(1.2,4.8,4.8),(-.83,.015,1.59),2.15)
    render("house-with-study.png",(4.5,5.5,7),(.20,.02,.55),5.25)
    render("study-writing-detail.png",(1.5,2.8,4),(-.98,.05,1.20),1.25)
for o in list(bpy.data.objects):
    if o.parent is None and o.name!="Camera" and o.type!="LIGHT":hide(o)
for n,a in enumerate(facts):place(a["id"],[(n%5-2)*.78,-.32+a["size"][1]/2,(n//5)*.80])
bpy.context.view_layer.update()
sheet_objects=[o for o in bpy.data.objects if o.type=="MESH" and not o.hide_render]
bpy.ops.mesh.primitive_plane_add(size=200,location=(0,0,-.324))
m=bpy.data.materials.new("Preview studio ground");m.diffuse_color=(.38,.40,.34,1);bpy.context.object.data.materials.append(m)
scene.render.resolution_x=2600;scene.render.resolution_y=1750
render("fourteen-study-models.png",(3.3,6,7),(0,.03,.65),5.05,sheet_objects)
