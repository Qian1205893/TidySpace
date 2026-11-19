"""Twelve authored PBR assets + continuous doorless room. Native metre/Y-up/+Z.

Private Blender scenes/manifests stay in /tmp; only shipped GLBs go into assets.
"""
import bpy,json,math,re,sys,subprocess
from pathlib import Path
from mathutils import Vector
HERE=Path(__file__).resolve().parent
ROOT=HERE.parent
PRIVATE=Path("/tmp/tidyspace-gaming-20261115")
sys.path.insert(0,str(HERE))
source=(HERE/"build_home_expansion.py").read_text()
exec(compile(source[:source.index("oak = atelier.pbr")],str(HERE/"build_home_expansion.py"),"exec"))
beveled_box=box
def box(name,parent,size,p,material,bevel=.003):
    if bevel>0:return beveled_box(name,parent,size,p,material,bevel)
    bpy.ops.mesh.primitive_cube_add(size=1,location=xyz(p))
    o=bpy.context.object;o.dimensions=(size[0],size[2],size[1])
    bpy.ops.object.transform_apply(location=False,rotation=False,scale=True)
    return finish(o,name,parent,material)
spec=json.loads((ROOT.parent/"TidySpace-gaming-renovation-20261115/design-spec.json").read_text())
declared=next(n["props"] for n in spec["nodes"] if n["id"]=="scene")
sizes={a["id"]:a["size"] for a in declared["equipment"]}
oak=atelier.pbr("Gaming warm oak","wood","A47B52",.54)
charcoal=atelier.pbr("Gaming graphite alloy","metal","343C40",.49,.45)
rubber=atelier.pbr("Gaming matte rubber","plain","242B2E",.82)
blue=atelier.pbr("Gaming slate upholstery","cloth","496777",.88)
cloth=atelier.pbr("Gaming woven desktop","cloth","526368",.89)
cream=atelier.pbr("Gaming ivory keycaps","plain","DCD8CB",.53)
brass=atelier.pbr("Gaming satin brass","metal","B59452",.38,.75)
plaster=atelier.pbr("Gaming quiet mineral","plain","DED8CA",.87)
glass=atelier.pbr("Gaming smoked side glass","glaze","758D99",.18,.05)
bs=glass.node_tree.nodes["Principled BSDF"]
bs.inputs["Alpha"].default_value=.24
glass.surface_render_method="DITHERED"
glass.use_backface_culling=False
def lightmat(name,c,strength=.65):
    m=atelier.pbr(name,"plain",c,.42)
    s=m.node_tree.nodes["Principled BSDF"]
    s.inputs["Emission Color"].default_value=(*atelier.linear(atelier.color(c)),1)
    s.inputs["Emission Strength"].default_value=strength
    return m
ice=lightmat("Gaming muted ice glow","96C5D7")
violet=lightmat("Gaming muted lavender glow","AF99C7",.55)
amber=lightmat("Gaming warm white glow","E7D7AE",.55)
screen=lightmat("Gaming screen night blue","183A4D",.45)
land=lightmat("Gaming screen mountain","517E82",.3)
sky=lightmat("Gaming screen horizon","B4A6B7",.35)
roots={k:empty(k) for k in sizes}
def text(parent,value,p,size=.0035,mat=charcoal):
    curve=bpy.data.curves.new("Key legend "+value,"FONT")
    curve.body=value;curve.size=size;curve.align_x="CENTER";curve.align_y="CENTER"
    o=bpy.data.objects.new(curve.name,curve);bpy.context.collection.objects.link(o)
    o.parent=parent;o.location=xyz(p);o.data.materials.append(mat)
    bpy.context.view_layer.objects.active=o;o.select_set(True)
    bpy.ops.object.convert(target="MESH");o.select_set(False)
    return o
def polygon(name,parent,pts,mat):
    mesh=bpy.data.meshes.new(name);mesh.from_pydata([xyz(p) for p in pts],[],[tuple(range(len(pts)))])
    mesh.update();o=bpy.data.objects.new(name,mesh);bpy.context.collection.objects.link(o)
    return finish(o,name,parent,mat)

r=roots["gamingdesk"]
box("Solid chamfered oak desktop",r,(.70,.025,.34),(0,.1675,0),oak,.004)
box("Recessed graphite apron",r,(.65,.018,.30),(0,.148,0),charcoal,.002)
for x in (-.30,.30):
    for z in (-.13,.13):
        box("Powder coated slim leg",r,(.022,.326,.022),(x,-.013,z),charcoal,.002)
        box("Rubber levelling foot",r,(.024,.009,.024),(x,-.1755,z),rubber,.001)
box("Cable tray",r,(.50,.018,.07),(0,.112,-.10),charcoal)
for n in range(13):
    box("Cable tray perforation",r,(.020,.001,.026),(-.23+n*.038,.122,-.10),rubber,.0004)
box("Underdesk low glow",r,(.55,.003,.004),(0,.132,.139),ice,.0005)
for x in (-.23,.23): ring("Brass cable grommet",r,.013,.0015,(x,.180,-.126),brass)
box("Back stretcher",r,(.61,.012,.012),(0,-.095,-.13),charcoal)

r=roots["gamingchair"]
ball("Tailored seat cushion",r,(.205,.043,.192),(0,-.045,.022),blue)
box("Seat shell",r,(.218,.022,.20),(0,-.071,.018),charcoal,.009)
ball("Ergonomic shaped back",r,(.192,.26,.060),(0,.080,-.082),blue)
ball("Lumbar pad",r,(.148,.068,.045),(0,.020,-.047),blue)
ball("Headrest",r,(.144,.060,.057),(0,.175,-.079),blue)
for x in (-.075,.075):
    line("Cream back seam",r,[(x,-.015,-.048),(x,.09,-.049),(x*.8,.174,-.052)],.0011,cream)
for x in (-.117,.117):
    rod("Arm support",r,(x,-.080,.015),(x,.015,.015),.006,charcoal)
    box("Soft armrest",r,(.026,.020,.145),(x,.025,.015),rubber,.007)
rod("Gas lift column",r,(0,-.197,0),(0,-.077,0),.011,charcoal)
for n in range(5):
    a=n*math.tau/5
    x,z=.115*math.cos(a),.126*math.sin(a)
    rod("Five star polished spoke",r,(0,-.175,0),(x,-.192,z),.007,charcoal)
    ball("Twin caster housing",r,(.025,.025,.025),(x,-.201,z),rubber)
    ring("Caster metallic rim",r,.008,.0015,(x,-.201,z+.013),brass,front=True)
rod("Seat adjustment lever",r,(.02,-.08,.04),(.10,-.08,.04),.004,charcoal)

r=roots["gamingpc"]
for y in (-.155,.155):
    box("Graphite case top and base",r,(.145,.01,.30),(0,y,0),charcoal,.003)
box("Graphite case solid left side",r,(.004,.30,.30),(-.0705,0,0),charcoal,.002)
box("Graphite case back",r,(.14,.30,.004),(0,0,-.148),charcoal,.002)
for x in (-.066,.066):
    box("Front fan frame side",r,(.008,.30,.008),(x,0,.145),charcoal,.001)
box("Power supply shroud",r,(.135,.04,.26),(0,-.132,-.01),charcoal,.002)
# Hollow glass side is shown against a recessed dark interior with real components.
box("Internal motherboard",r,(.002,.26,.23),(.070,0,-.006),rubber,.001)
box("Window clear panel",r,(.002,.263,.245),(.074,0,-.006),glass,.001)
for y in (-.135,.135):
    box("Window metal horizontal trim",r,(.007,.010,.264),(.074,y,-.004),charcoal)
for z in (-.134,.126):
    box("Window metal vertical trim",r,(.007,.273,.008),(.074,0,z),charcoal)
box("GPU metal shroud",r,(.012,.044,.19),(.074,-.049,-.004),charcoal)
for n in range(2):
    for j in range(12):
        box("GPU fin",r,(.014,.002,.004),(.081,-.032+j*.002,-.075+n*.082),brass,.0001)
for y in (-.095,0,.095):
    ring("Front fan glowing ring",r,.035,.003,(0,y,.152),ice,front=True)
    ring("Fan outer rim",r,.039,.003,(0,y,.151),charcoal,front=True)
    ball("Fan hub",r,(.015,.015,.005),(0,y,.156),charcoal)
    for n in range(7):
        a=n*math.tau/7
        polygon("Fan blade",r,[(.006*math.cos(a),y+.006*math.sin(a),.154),
            (.029*math.cos(a+.25),y+.029*math.sin(a+.25),.154),
            (.027*math.cos(a+.75),y+.027*math.sin(a+.75),.154)],rubber)
for x in (-.052,.052):
    for z in (-.115,.115):box("PC rubber foot",r,(.017,.012,.025),(x,-.166,z),rubber)
for z in (-.07,-.025,.02):
    box("Top port recess",r,(.020,.002,.008),(.033,.162,z),rubber,.001)
ball("Power indicator",r,(.008,.002,.008),(-.036,.163,.09),violet)

r=roots["gamingmonitor"]
box("Ultra wide screen frame",r,(.39,.157,.015),(0,.025,-.008),charcoal,.004)
box("Recessed game screen",r,(.377,.143,.001),(0,.025,.0005),screen,.001)
polygon("Screen distant peaks",r,[(-.188,-.005,.0014),(-.125,.070,.0014),(-.055,.012,.0014),
    (.015,.060,.0014),(.095,.015,.0014),(.188,.05,.0014),(.188,-.02,.0014),(-.188,-.02,.0014)],sky)
polygon("Screen foreground ridge",r,[(-.188,-.04,.0017),(-.072,.030,.0017),(.02,-.015,.0017),
    (.119,.047,.0017),(.188,-.012,.0017),(.188,-.045,.0017)],land)
polygon("Racing road ribbon",r,[(-.11,-.045,.002),(.011,.022,.002),(.025,.022,.002),(-.046,-.045,.002)],charcoal)
for n in range(7):
    box("Screen HUD minimal telemetry",r,(.013,.002,.0005),(-.165+n*.022,.085,.002),ice,.0001)
box("Monitor chin",r,(.15,.004,.002),(0,-.055,.001),charcoal)
ball("Monitor status light",r,(.003,.002,.001),(.17,-.05,.002),ice)
rod("Monitor height stand",r,(0,-.10,-.024),(0,-.037,-.024),.009,charcoal)
box("Monitor low oak base",r,(.15,.011,.075),(0,-.1095,-.01),oak,.004)
box("Monitor top light rail",r,(.25,.006,.011),(0,.107,.006),charcoal,.002)
box("Warm desk light diffuser",r,(.237,.001,.008),(0,.103,.008),amber,.0004)

r=roots["gamingkeyboard"]
box("Keyboard aluminium base",r,(.225,.012,.078),(0,-.006,0),charcoal,.003)
box("Keyboard soft perimeter glow",r,(.222,.002,.075),(0,.0004,0),violet,.001)
for row in range(5):
    for col in range(14):
        x=-.102+col*.0157;z=-.029+row*.0143
        if row==4 and 3<=col<=8:continue
        box("Double shot keycap",r,(.0132,.009,.0118),(x,.006,z),
            blue if col==0 or (row==0 and col==13) else cream,.0012)
        legend=("1234567890-+=" if row==0 else "QWERTYUIOP[]" if row==1 else "ASDFGHJKL;" if row==2 else "ZXCVBNM,." if row==3 else "")
        if col<len(legend):text(r,legend[col],(x,.0107,z),.0034)
box("Wide spacebar",r,(.09,.009,.0118),(-.016,.006,.0282),cream,.0012)

r=roots["gamingmouse"]
ball("Sculpted mouse shell",r,(.038,.027,.064),(0,0,0),cream)
box("Split button seam",r,(.0007,.002,.026),(0,.012,-.013),charcoal,.0001)
rod("Rubber scroll wheel",r,(0,.010,-.014),(0,.010,-.004),.003,rubber)
line("Mouse understated light edge",r,[(-.015,-.003,.02),(0,-.010,.03),(.015,-.003,.02)],.0009,ice)
for x in (-.009,.009):box("Glide skate",r,(.010,.001,.014),(x,-.013,.015),rubber,.001)

r=roots["gamingmat"]
box("Fabric desktop pad",r,(.405,.004,.14),(0,0,0),cloth,.001)
for z in (-.068,.068):
    for n in range(65):box("Overlocked fabric stitch",r,(.002,.0002,.0004),(-.195+n*.006,.002,z),cream,0)
for x in (-.201,.201):
    for n in range(22):box("Side stitched edge",r,(.0004,.0002,.002),(x,.002,-.063+n*.006),cream,0)

r=roots["gamingheadset"]
box("Oak headphone dock",r,(.083,.009,.075),(0,-.0755,0),oak,.005)
rod("Headphone stand",r,(0,-.070,0),(0,.049,0),.0045,brass)
box("Curved top saddle",r,(.036,.006,.025),(0,.052,0),oak,.003)
arc=[(.034*math.cos(a),.028+.048*math.sin(a),.008) for a in [n*math.pi/24 for n in range(25)]]
line("Padded headphone headband",r,arc,.0055,charcoal)
line("Headband slate inner cushion",r,[(x,y-.005,z+.001) for x,y,z in arc],.002,blue)
for x in (-.034,.034):
    ball("Over ear graphite cup",r,(.022,.043,.029),(x,.01,.008),charcoal)
    ball("Soft textile ear pad",r,(.020,.037,.020),(x,.010,.020),blue)
rod("Headset microphone",r,(-.04,.006,.017),(-.039,-.022,.042),.0015,charcoal)

r=roots["gamingspeakers"]
for x in (-.24,.24):
    box("Oak acoustic enclosure",r,(.075,.10,.069),(x,0,0),oak,.004)
    box("Speaker baffle",r,(.067,.090,.003),(x,0,.034),charcoal,.003)
    for y,rad in ((-.018,.023),(.030,.010)):
        ring("Speaker cloth surround",r,rad,.0025,(x,y,.037),rubber,front=True)
        ball("Speaker cone",r,(rad*1.8,rad*1.8,.003),(x,y,.038),charcoal)
        ball("Speaker dome",r,(rad*.60,rad*.60,.005),(x,y,.040),rubber)
    rod("Acoustic port",r,(x,-.038,.035),(x,-.038,.041),.005,rubber)

r=roots["gaminggamepad"]
box("Charging cradle",r,(.065,.012,.054),(0,-.03,0),oak,.005)
ball("Controller body",r,(.098,.042,.05),(0,.004,0),blue)
for x in (-.037,.037):ball("Ergonomic controller grip",r,(.027,.043,.039),(x,-.01,.014),charcoal)
for x in (-.022,.013):
    rod("Analogue stick stem",r,(x,.022,.01),(x,.030,.01),.004,charcoal)
    ball("Thumb stick pad",r,(.012,.004,.012),(x,.032,.01),rubber)
for x,z in ((.031,-.011),(.037,-.018),(.025,-.018),(.031,-.025)):
    ball("Face button",r,(.006,.005,.006),(x,.024,z),ice if z==-.011 else violet)
box("Dpad horizontal",r,(.015,.004,.005),(-.022,.026,-.019),cream,.0006)
box("Dpad vertical",r,(.005,.004,.015),(-.022,.026,-.019),cream,.0006)

r=roots["gamingstorage"]
box("Oak equipment cabinet",r,(.19,.22,.17),(0,.01,0),oak,.004)
box("Graphite cabinet door",r,(.174,.181,.009),(0,.017,.084),charcoal,.003)
for n in range(13):box("Oak slatted door face",r,(.008,.159,.003),(-.073+n*.012,.017,.089),oak,.001)
rod("Cabinet small brass pull",r,(.053,.009,.093),(.053,.045,.093),.0025,brass)
for x in (-.074,.074):
    for z in (-.056,.056):rod("Cabinet slender leg",r,(x,-.12,z),(x,-.09,z),.005,charcoal)
box("Soft cabinet top inlay",r,(.172,.002,.151),(0,.121,0),cloth,.001)

r=roots["gaminglight"]
box("Lighting weighted base",r,(.065,.013,.065),(0,-.2135,0),charcoal,.005)
box("Vertical lamp spine",r,(.015,.42,.019),(0,.009,0),charcoal,.004)
box("Diffused light column",r,(.009,.40,.003),(0,.013,.011),ice,.002)
box("Warm lower column",r,(.009,.065,.003),(0,-.155,.0115),amber,.001)

# Match the catalogue pivots exactly. Native normals/materials remain independent.
bpy.context.view_layer.update()
for name,root in roots.items():
    points=[root.matrix_world.inverted()@o.matrix_world@Vector(c) for o in root.children_recursive if o.type=="MESH" for c in o.bound_box]
    lo=Vector([min(p[k] for p in points) for k in range(3)])
    hi=Vector([max(p[k] for p in points) for k in range(3)])
    size=sizes[name];factor=Vector((size[0]/(hi.x-lo.x),size[2]/(hi.y-lo.y),size[1]/(hi.z-lo.z)))
    for o in root.children:
        o.location=(o.location-(lo+hi)/2)*factor;o.scale*=factor
for o in bpy.data.objects:
    if o.type=="MESH":atelier.project(o,tile=.075 if o.data.materials[0] in (blue,cloth) else .48)
atelier.bake_occlusion()
def batch(parents):
    for parent in parents:
        meshes=[o for o in parent.children if o.type=="MESH"]
        if not meshes:continue
        bpy.ops.object.select_all(action="DESELECT")
        for o in meshes:o.select_set(True)
        bpy.context.view_layer.objects.active=meshes[0]
        bpy.ops.object.join();o=bpy.context.object;o.name=parent.name+"_mesh"
        mod=o.modifiers.new("Native triangles","TRIANGULATE");bpy.ops.object.modifier_apply(modifier=mod.name)
def export(path,chosen):
    bpy.ops.object.select_all(action="DESELECT")
    for r in chosen:
        r.select_set(True)
        for o in r.children_recursive:o.select_set(True)
    bpy.ops.export_scene.gltf(filepath=str(path),export_format="GLB",use_selection=True,
        export_yup=True,export_apply=True,export_tangents=True,export_animations=False)
    subprocess.run(["/usr/bin/python3",str(HERE/"repair_degenerate_tangents.py"),str(path)],check=True)
batch(roots.values())
for name,root in roots.items():
    mesh=next(o for o in root.children if o.type=="MESH")
    bpy.context.view_layer.objects.active=mesh
    bpy.ops.object.select_all(action="DESELECT");mesh.select_set(True)
    bpy.ops.object.transform_apply(location=True,rotation=True,scale=True)
    verts=[v.co.copy() for v in mesh.data.vertices]
    lo=Vector([min(p[k] for p in verts) for k in range(3)])
    hi=Vector([max(p[k] for p in verts) for k in range(3)])
    size=sizes[name]
    factor=Vector((size[0]/(hi.x-lo.x),size[2]/(hi.y-lo.y),size[1]/(hi.z-lo.z)))
    for v in mesh.data.vertices:v.co=(v.co-(lo+hi)/2)*factor
    mesh.data.update()
bpy.context.view_layer.update()
manifest={}
for name,root in roots.items():
    pts=[root.matrix_world.inverted()@o.matrix_world@Vector(c) for o in root.children_recursive if o.type=="MESH" for c in o.bound_box]
    pts=[(p.x,p.z,-p.y) for p in pts]
    manifest[name]=dict(size=sizes[name],min=[min(p[k] for p in pts) for k in range(3)],max=[max(p[k] for p in pts) for k in range(3)],
        triangles=sum(len(o.data.polygons) for o in root.children_recursive if o.type=="MESH"),front="+Z",unit="metre")
export(ROOT/"app/src/main/assets/models/tidy-gaming.glb",roots.values())
(PRIVATE/"gaming-manifest.json").write_text(json.dumps(manifest,indent=2))
bpy.ops.wm.save_as_mainfile(filepath=str(PRIVATE/"gaming-models.blend"))

bpy.ops.object.select_all(action="SELECT");bpy.ops.object.delete(use_global=False)
house=empty("gaming_house")
floor=empty("gaming_continuous_floor",house)
left,right,back,front=-.10265,1.5747,.899,2.48
cx,cz=(left+right)/2,(back+front)/2
box("Continuous oak foundation",floor,(right-left+.028,.060,front-back+.028),(cx,-.358,cz),oak,.005)
box("Recessed parquet backing",floor,(right-left,.016,front-back),(cx,-.330,cz),charcoal,.001)
atelier.parquet(floor,(left,right,back,front))
box("Flush old shell overlap",floor,(right-left,.0015,.058),(cx,-.31925,back+.029),oak,.0003)
# Both annex areas are open; no partition or wall-mounted light remains.
for o in bpy.data.objects:
    if o.type=="MESH":atelier.project(o,tile=.48)
atelier.bake_occlusion();batch([floor])
export(ROOT/"app/src/main/assets/models/tidy-gaming-room.glb",[house])
bpy.ops.wm.save_as_mainfile(filepath=str(PRIVATE/"gaming-room.blend"))
print("GAMING_ASSETS_COMPLETE",len(manifest),flush=True)
