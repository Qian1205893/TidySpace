"""Eight articulated palm toys. Blender source, native-metre GLB and studio contact sheet."""
import bpy, math, json, wave, struct
import sys
from pathlib import Path
from mathutils import Vector

HERE=Path(__file__).resolve().parent
sys.path.insert(0,str(HERE))
import art_direction as atelier
OUT=HERE/"playthings"
ASSETS=HERE.parent/"app/src/main/assets"
OUT.mkdir(exist_ok=True)
bpy.ops.object.select_all(action="SELECT")
bpy.ops.object.delete(use_global=False)
def xyz(p): return (p[0],-p[2],p[1])
def mat(name,c,rough=.38,metal=0):
    m=bpy.data.materials.new(name);m.use_nodes=True
    p=m.node_tree.nodes.get("Principled BSDF")
    p.inputs["Base Color"].default_value=(*c,1)
    p.inputs["Roughness"].default_value=rough;p.inputs["Metallic"].default_value=metal
    return m
cream=mat("Warm porcelain",(.90,.84,.71),.28)
white=mat("Soft rice dough",(.96,.91,.82),.7)
mint=mat("Mint jelly",(.27,.68,.51),.18)
red=mat("Candy red lacquer",(.65,.022,.033),.22)
ink=mat("Midnight charcoal",(.022,.032,.033),.3)
gold=mat("Satin brass",(.61,.37,.12),.29,.78)
pink=mat("Rose cheeks",(.89,.32,.38),.6)
wood=mat("Turned honey wood",(.48,.23,.07),.38)
skin=mat("Woven drum skin",(.81,.68,.43),.7)
blue=mat("Penguin blue ceramic",(.075,.21,.31),.25)
star=mat("Warm star",(.95,.62,.10),.22,.15)
bs=star.node_tree.nodes.get("Principled BSDF")
bs.inputs["Emission Color"].default_value=(.8,.35,.025,1);bs.inputs["Emission Strength"].default_value=.5
pastels=[mat("Bubble "+str(i),c,.44) for i,c in enumerate([
    (.87,.31,.36),(.94,.56,.20),(.89,.76,.24),(.26,.63,.45),(.24,.49,.74),(.56,.36,.69)])]
# Image-based grain is visible in the native renderer, unlike procedural-only shaders.
for material,texture in ((wood,"oak"),(skin,"cushion")):
    tree=material.node_tree;bs=tree.nodes.get("Principled BSDF")
    for suffix,slot in (("base","Base Color"),("rough","Roughness"),("normal","Normal")):
        image=bpy.data.images.load(str(HERE/"furniture"/f"{texture}_{suffix}.png"),check_existing=True)
        if suffix!="base": image.colorspace_settings.name="Non-Color"
        image.pack();node=tree.nodes.new("ShaderNodeTexImage");node.image=image
        if suffix=="normal":
            normal=tree.nodes.new("ShaderNodeNormalMap");normal.inputs["Strength"].default_value=.2
            tree.links.new(node.outputs["Color"],normal.inputs["Color"])
            tree.links.new(normal.outputs["Normal"],bs.inputs[slot])
        else: tree.links.new(node.outputs["Color"],bs.inputs[slot])

def empty(name,parent=None,p=(0,0,0)):
    o=bpy.data.objects.new(name,None);bpy.context.collection.objects.link(o)
    o.parent=parent;o.location=xyz(p);return o
def finish(o,name,parent,m):
    o.name=name;o.parent=parent;o.data.materials.append(m)
    for f in o.data.polygons:f.use_smooth=True
    return o
def ball(name,parent,size,p,m):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=40,ring_count=24,radius=1,location=xyz(p))
    o=bpy.context.object;o.scale=(size[0]/2,size[2]/2,size[1]/2)
    bpy.ops.object.transform_apply(location=False,rotation=False,scale=True)
    return finish(o,name,parent,m)
def box(name,parent,size,p,m,bevel=.006):
    bpy.ops.mesh.primitive_cube_add(size=1,location=xyz(p))
    o=bpy.context.object;o.dimensions=(size[0],size[2],size[1])
    bpy.ops.object.transform_apply(location=False,rotation=False,scale=True)
    b=o.modifiers.new("Soft moulded edge","BEVEL");b.width=min(bevel,min(size)*.48);b.segments=5
    bpy.ops.object.modifier_apply(modifier=b.name)
    return finish(o,name,parent,m)
def rod(name,parent,a,b,r,m):
    delta=Vector(xyz(b))-Vector(xyz(a))
    bpy.ops.mesh.primitive_cylinder_add(vertices=32,radius=r,depth=delta.length,
        location=(Vector(xyz(a))+Vector(xyz(b)))/2)
    o=bpy.context.object;o.rotation_mode="QUATERNION";o.rotation_quaternion=delta.to_track_quat("Z","Y")
    return finish(o,name,parent,m)
def lathe(name,parent,profile,m,p=(0,0,0)):
    n=64;verts=[];faces=[]
    for r,y in profile:
        for i in range(n+1):
            a=math.tau*i/n;verts.append(xyz((p[0]+r*math.cos(a),p[1]+y,p[2]+r*math.sin(a))))
    for j in range(len(profile)-1):
        for i in range(n):
            a=j*(n+1)+i;faces.append((a,a+n+1,a+n+2,a+1))
    mesh=bpy.data.meshes.new(name);mesh.from_pydata(verts,[],faces);mesh.update()
    o=bpy.data.objects.new(name,mesh);bpy.context.collection.objects.link(o)
    uv=mesh.uv_layers.new()
    for f in mesh.polygons:
        for loop in f.loop_indices:
            v=mesh.loops[loop].vertex_index;uv.data[loop].uv=((v%(n+1))/n,(v//(n+1))/max(1,len(profile)-1))
    return finish(o,name,parent,m)
def torus(name,parent,r,t,p,m):
    return lathe(name,parent,[(r+t*math.cos(a),t*math.sin(a)) for a in [i*math.tau/16 for i in range(17)]],m,p)
def line(name,parent,points,r,m):
    for a,b in zip(points,points[1:]):rod(name,parent,a,b,r,m)
def star_mesh(name,parent,p,size,m):
    vs=[];fs=[]
    for z in (-.004,.004):
        for i in range(10):
            a=math.pi/2+math.tau*i/10;r=size if i%2==0 else size*.46
            vs.append(xyz((p[0]+math.cos(a)*r,p[1]+math.sin(a)*r,p[2]+z)))
    fs.extend([tuple(range(9,-1,-1)),tuple(range(10,20))])
    for i in range(10):fs.append((i,(i+1)%10,(i+1)%10+10,i+10))
    mesh=bpy.data.meshes.new(name);mesh.from_pydata(vs,[],fs);mesh.update()
    o=bpy.data.objects.new(name,mesh);bpy.context.collection.objects.link(o)
    b=o.modifiers.new("Star bevel","BEVEL");b.width=.001;b.segments=3
    bpy.context.view_layer.objects.active=o;bpy.ops.object.modifier_apply(modifier=b.name)
    return finish(o,name,parent,m)

roots={k:empty(k) for k in ("rattle","pokeball","mochi","jelly","popboard","spinner","tumbler","ratchet")}
r=roots["rattle"]
lathe("Standing handle",r,[(0,-.11),(.022,-.11),(.023,-.107),(.014,-.103),(.011,-.097),
    (.009,-.022),(.014,-.008),(.012,.005),(0,.010)],wood)
for y in (-.082,-.076,-.030):torus("Handle brass band",r,.0095,.001,(0,y,0),gold)
rod("Red drum frame",r,(0,.051,-.017),(0,.051,.017),.056,red)
for z in (-.019,.019):
    rod("Natural skin",r,(0,.051,z-.0006),(0,.051,z+.0006),.051,skin)
    for i in range(20):
        a=i*math.tau/20;ball("Tension pin",r,(.003,.003,.003),(.053*math.cos(a),.051+.053*math.sin(a),z),gold)
star_mesh("Drum sun",r,(0,.051,.0205),.018,red)
for side in (-1,1):
    pivot=empty("rattle_left" if side<0 else "rattle_right",r,(side*.053,.051,0))
    line("Braided cord",pivot,[(0,0,0),(side*.010,-.004,.001),(side*.021,-.009,.002),(side*.026,-.019,.004)],.0014,cream)
    ball("Wooden drum bead",pivot,(.016,.016,.016),(side*.028,-.022,.004),wood)

r=roots["pokeball"]
lathe("Ball cradle",r,[(0,-.07),(.036,-.07),(.040,-.066),(.038,-.057),(.027,-.055),(0,-.06)],ink)
lathe("Lower white shell",r,[(.064*math.cos(a),.002-.064*math.sin(a)) for a in [i*math.pi/2/24 for i in range(25)]]+
    [(0,-.057)]+[(.059*math.cos(a),.002-.059*math.sin(a)) for a in [math.pi/2-i*math.pi/2/24 for i in range(25)]],cream)
torus("Black equatorial band",r,.061,.0035,(0,.002,0),ink)
lid=empty("pokeball_lid",r,(0,.002,-.060))
lathe("Red upper shell",lid,[(.064*math.cos(a),.064*math.sin(a)) for a in [i*math.pi/2/24 for i in range(25)]]+
    [(0,.059)]+[(.059*math.cos(a),.059*math.sin(a)) for a in [math.pi/2-i*math.pi/2/24 for i in range(25)]],red,(0,0,.060))
rod("Rear hinge",r,(-.018,.002,-.061),(.018,.002,-.061),.005,ink)
rod("Button rim",r,(0,.002,.061),(0,.002,.070),.015,ink)
rod("Pearl release button",r,(0,.002,.070),(0,.002,.073),.010,cream)
surprise=empty("pokeball_star",r)
star_mesh("Secret wishing star",surprise,(0,.017,0),.025,star)
for i in range(3):ball("Star spark",surprise,(.004,.004,.004),((i-1)*.028,.045+i*.004,0),star)

r=roots["mochi"]
lathe("Small mochi plate",r,[(0,-.05),(.057,-.05),(.063,-.047),(.065,-.042),(.061,-.039),(0,-.040)],cream)
soft=empty("mochi_soft",r,(0,-.039,0))
ball("Round rice dumpling",soft,(.112,.086,.10),(0,.043,0),white)
for x in (-.021,.021):
    ball("Glossy seed eye",soft,(.006,.009,.003),(x,.050,.0455),ink)
    ball("Eye glint",soft,(.0017,.002,.001),(x-.001,.052,.047),cream)
    ball("Blushing cheek",soft,(.014,.006,.002),(x*1.48,.041,.042),pink)
line("Tiny smile",soft,[(-.007,.037,.0475),(0,.034,.049),(.007,.037,.0475)],.0012,ink)
ball("Dough peak",soft,(.023,.014,.022),(0,.083,0),white)

r=roots["jelly"]
box("Jelly tray",r,(.12,.009,.12),(0,-.048,0),cream,.004)
soft=empty("jelly_soft",r,(0,-.0435,0))
box("Soft mint cube",soft,(.094,.096,.094),(0,.048,0),mint,.018)
for x in (-.018,.018):ball("Jelly eye",soft,(.005,.008,.003),(x,.048,.047),ink)
line("Jelly grin",soft,[(-.006,.036,.0478),(0,.034,.048),(.006,.036,.0478)],.001,ink)
for x,y in ((-.027,.072),(.023,.080),(-.031,.020)):
    ball("Suspended sugar fleck",soft,(.005,.008,.0015),(x,y,.046),cream)
box("Soft highlight",soft,(.009,.044,.001),( -.033,.055,.0465),mat("Jelly highlight",(.50,.80,.65),.2),.0003)

r=roots["popboard"]
box("Rounded rainbow frame",r,(.20,.030,.17),(0,-.005,0),cream,.014)
box("Inset silicone field",r,(.18,.003,.15),(0,.011,0),ink,.001)
for i in range(6):
    x=(i%3-1)*.056;z=(i//3-.5)*.068
    torus("Bubble lip",r,.023,.002,(x,.013,z),pastels[i])
    bubble=empty("popboard_"+str(i),r,(x,.013,z))
    lathe("Reversible dome",bubble,[(.022,0),(.021,.004),(.018,.008),(.012,.012),(0,.014)],pastels[i])
for x in (-.09,.09):rod("Frame fastener",r,(x,-.001,.066),(x,.012,.066),.003,gold)

r=roots["spinner"]
lathe("Bearing foot",r,[(0,-.0175),(.025,-.0175),(.026,-.013),(.020,-.008),(0,-.008)],ink)
rotor=empty("spinner_rotor",r)
torus("Central bearing",rotor,.020,.004,(0,0,0),gold)
for i in range(3):
    a=i*math.tau/3;x=.048*math.cos(a);z=.048*math.sin(a)
    rod("Rotor spoke",rotor,(x*.3,0,z*.3),(x,0,z),.011,pastels[2*i])
    torus("Outer balance bearing",rotor,.019,.005,(x,0,z),pastels[2*i])
    torus("Steel inner ring",rotor,.013,.003,(x,.001,z),gold)
lathe("Finger cap",r,[(0,.003),(.019,.003),(.019,.010),(.016,.016),(0,.0175)],cream)
star_mesh("Cap emblem",r,(0,.009,.017),.006,gold)

r=roots["tumbler"]
body=empty("tumbler_body",r,(0,-.08,0))
lathe("Weighted curved bottom",body,[(0,0),(.018,.004),(.037,.014),(.048,.030),(.051,.050),
    (.049,.071),(.042,.086),(0,.091)],blue)
ball("Penguin head",body,(.081,.077,.074),(0,.120,0),blue)
ball("Cream bib",body,(.065,.086,.015),(0,.082,.039),cream)
for x in (-.023,.023):
    ball("Eye patch",body,(.030,.033,.010),(x,.126,.030),cream)
    ball("Bright eye",body,(.008,.010,.005),(x,.128,.036),ink)
    ball("Eye light",body,(.002,.003,.001),(x-.001,.130,.039),white)
ball("Little beak",body,(.017,.013,.022),(0,.115,.043),gold)
for x in (-.044,.044):ball("Flipper",body,(.021,.056,.032),(x,.082,0),blue)
torus("Mint scarf",body,.036,.006,(0,.099,0),mint)
box("Scarf tail",body,(.014,.037,.009),(.020,.078,.046),mint,.004)

r=roots["ratchet"]
lathe("Knob pedestal",r,[(0,-.030),(.052,-.030),(.057,-.025),(.057,-.008),(.053,-.004),(0,-.004)],cream)
for i in range(12):
    a=i*math.tau/12
    rod("Detent index",r,(.044*math.sin(a),-.003,.044*math.cos(a)),
        (.050*math.sin(a),-.003,.050*math.cos(a)),.0011,ink)
knob=empty("ratchet_knob",r)
lathe("Turned brass dial",knob,[(0,-.002),(.036,-.002),(.039,.003),(.039,.022),(.035,.028),(0,.030)],gold)
for i in range(48):
    a=i*math.tau/48
    rod("Knurled grip",knob,(.039*math.cos(a),.005,.039*math.sin(a)),
        (.039*math.cos(a),.021,.039*math.sin(a)),.0008,wood)
rod("Dial pointer",knob,(0,.030,.018),(0,.030,.031),.002,ink)

# Combine static geometry under each authored pivot. Shared mesh/material resources, independent transforms.
atelier.apply()
parents=list(roots.values())+[o for o in bpy.data.objects if o.type=="EMPTY" and o not in roots.values()]
for parent in parents:
    meshes=[o for o in parent.children if o.type=="MESH"]
    if not meshes:continue
    bpy.ops.object.select_all(action="DESELECT")
    for o in meshes:o.select_set(True)
    bpy.context.view_layer.objects.active=meshes[0]
    if len(meshes)>1:bpy.ops.object.join()
    o=bpy.context.object;o.name=parent.name+"_mesh"
    bpy.ops.object.transform_apply(location=False,rotation=True,scale=True)
    mod=o.modifiers.new("GLTF triangles","TRIANGULATE");bpy.ops.object.modifier_apply(modifier=mod.name)
bpy.context.view_layer.update()
manifest={}
for name,root in roots.items():
    points=[root.matrix_world.inverted()@o.matrix_world@Vector(c)
        for o in root.children_recursive if o.type=="MESH" for c in o.bound_box]
    points=[(p.x,p.z,-p.y) for p in points]
    manifest[name]={"min":[min(p[k] for p in points) for k in range(3)],
        "max":[max(p[k] for p in points) for k in range(3)],
        "pivots":[o.name for o in root.children_recursive if o.type=="EMPTY"]}
(OUT/"manifest.json").write_text(json.dumps(manifest,indent=2))
bpy.ops.object.select_all(action="SELECT")
bpy.ops.export_scene.gltf(filepath=str(ASSETS/"models/tidy-playthings.glb"),export_format="GLB",
    export_yup=True,export_apply=True,export_tangents=True,export_animations=False)
if "--export-only" in sys.argv:
    print("TIDY_PLAYTHINGS_EXPORT_COMPLETE",flush=True)
    sys.exit(0)

def sound(name,duration,partials):
    rate=24000
    with wave.open(str(ASSETS/"sounds"/f"{name}.wav"),"wb") as f:
        f.setparams((1,2,rate,0,"NONE","not compressed"))
        f.writeframes(b"".join(struct.pack("<h",int(max(-.9,min(.9,
            sum(amp*math.sin(math.tau*hz*n/rate)*math.exp(-decay*n/rate)
                for hz,amp,decay in partials)*min(1,n/48)))*32767)) for n in range(int(rate*duration))))
sound("rattle",.18,[(210,.4,30),(380,.25,40),(750,.12,55)])
sound("pop",.16,[(380,.4,40),(185,.25,50),(1100,.05,80)])
sound("click",.09,[(1700,.3,100),(800,.20,75)])
sound("squish",.32,[(160,.16,12),(230,.12,17),(440,.035,20)])
sound("spark",.65,[(1046,.18,6),(1318,.12,7),(1568,.09,8)])
for index,(name,root) in enumerate(roots.items()):
    root.location=xyz(((index%4-1.5)*.30,-manifest[name]["min"][1],(index//4-.5)*.32))
    if name=="pokeball":lid.rotation_euler.x=math.radians(-105)
floor=mat("Studio pale sage",(.69,.74,.68),.9)
box("Studio surface",None,(1.9,.025,1.15),(0,-.019,0),floor,.01)
bpy.ops.object.camera_add(location=(.85,-1.35,1.10))
camera=bpy.context.object;camera.rotation_euler=(Vector((0,0,.03))-camera.location).to_track_quat("-Z","Y").to_euler()
camera.data.type="ORTHO";camera.data.ortho_scale=1.43;bpy.context.scene.camera=camera
for loc,power,size in (((-.6,-.6,1.6),120,1.1),((.8,.5,1.0),90,.9)):
    bpy.ops.object.light_add(type="AREA",location=loc);light=bpy.context.object
    light.data.energy=power;light.data.shape="DISK";light.data.size=size
    light.rotation_euler=(-light.location).to_track_quat("-Z","Y").to_euler()
scene=bpy.context.scene;scene.render.engine="CYCLES";scene.cycles.samples=40
scene.render.resolution_x=1800;scene.render.resolution_y=1200;scene.render.resolution_percentage=100
scene.world.color=(.35,.35,.35)
atelier.warm_preview(scene)
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/"TidySpace-playthings.blend"))
scene.render.filepath=str(OUT/"blender-inspection.png");bpy.ops.render.render(write_still=True)
