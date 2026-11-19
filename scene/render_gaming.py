"""Offline previews of the exact GLBs shipped into Android; never save a showcase layout."""
import bpy,json,math
from pathlib import Path
from mathutils import Vector
HERE=Path(__file__).resolve().parent
source=(HERE/"render_bathroom.py").read_text()
exec(compile(source[:source.index('render("bathroom-detail.png"')],str(HERE/"render_bathroom.py"),"exec"))
OUT=ROOT.parent/"TidySpace-gaming-renovation-20261115"
for family in ("gaming","gaming-room"):
    bpy.ops.import_scene.gltf(filepath=str(ROOT/"app/src/main/assets/models"/f"tidy-{family}.glb"))
objects={o.name:o for o in bpy.data.objects}
hide(objects["entryway"])
design=json.loads((OUT/"design-spec.json").read_text())
facts=next(n["props"] for n in design["nodes"] if n["id"]=="scene")
for a in facts["equipment"]:
    place(a["id"],a["position"],180 if a["id"]=="gamingchair" else 0)
scene.cycles.samples=40
scene.render.resolution_x=2000;scene.render.resolution_y=1600
render("gaming-room-detail.png",(2.8,3.8,5.4),(.70,.03,1.94),1.92)
render("house-with-gaming-room.png",(4.5,5.5,7),(.2,.05,.63),5.38)
render("floor-connection.png",(.2,6,1.5),(.2,-.32,1.32),3.45)
# Every newly generated asset visible once in a separate inspection arrangement.
for o in bpy.data.objects:
    if o.parent is None and o.name!="Camera" and o.type!="LIGHT":hide(o)
for n,a in enumerate(facts["equipment"]):
    x=(n%4-1.5)*.82;z=(n//4)*.82
    p=[x,-.32+a["size"][1]/2,z]
    place(a["id"],p)
bpy.ops.mesh.primitive_plane_add(size=200,location=(0,0,-.324))
ground=bpy.context.object
m=bpy.data.materials.new("Preview studio ground");m.diffuse_color=(.32,.36,.36,1)
ground.data.materials.append(m)
scene.render.resolution_x=2400;scene.render.resolution_y=1750
render("twelve-gaming-models.png",(4,5,7),(0,.02,.77),3.65)
