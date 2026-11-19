"""Incremental, metre/Y-up PBR assets. Source helpers belong to this project."""
import bpy, math, json, random
from pathlib import Path
from mathutils import Vector

HERE=Path(__file__).resolve().parent
source=(HERE/"build_furniture.py").read_text()
exec(compile(source[:source.index("roots = {")],str(HERE/"build_furniture.py"),"exec"))
OUT=HERE/"moving-home"
OUT.mkdir(exist_ok=True)
random.seed(261)
carton=material("Natural corrugated kraft fibre","C7A779",.93,
    texture=maps("carton","C7A779","clay",512))
tape=material("Paper packing tape","DCC19A",.79)
ivory=material("Cotton paper label","F3E9D4",.9)
ink=material("Printed forest ink","496553",.88)
cream=material("Chalk painted joinery","F3E8D1",.6)
black=material("Charred log","33261F",.96)
flame=material("Amber flame","FF9328",.7,emission=3)
heart=material("Golden flame core","FFE19C",.7,emission=4)
embermat=material("Live ember","EB4D16",.85,emission=2)
roots={}
def root(name,p=(0,0,0)):
    roots[name]=empty(name,p=p)
    return roots[name]
def sphere(name,parent,radii,p,mat):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=16,ring_count=8,radius=1,location=xyz(p))
    o=bpy.context.object
    o.scale=(radii[0],radii[2],radii[1])
    bpy.ops.object.transform_apply(location=False,rotation=False,scale=True)
    return finish(o,name,parent,mat)
def text(name,parent,value,p,size,mat=ink):
    curve=bpy.data.curves.new(name,"FONT")
    curve.body=value;curve.size=size;curve.align_x="CENTER";curve.align_y="CENTER"
    curve.extrude=.00008
    o=bpy.data.objects.new(name,curve);bpy.context.collection.objects.link(o)
    o.parent=parent;o.location=xyz(p);o.rotation_euler=(math.pi/2,0,0)
    o.data.materials.append(mat)
    bpy.context.view_layer.objects.active=o;o.select_set(True)
    bpy.ops.object.convert(target="MESH");o.select_set(False)
    return o

# Open folded cartons: real hollow bodies, raised rims, handles and shrinking paper fill.
for key,label,tone in [
    ("furniture","FURNITURE","B58456"),("light","LIGHT","D6AF63"),
    ("home","LIVING","789882"),("toys","JOY","BC8C84")]:
    r=root("movingbox-"+key)
    box("Carton bottom",r,(.25,.008,.23),(0,-.096,0),carton,.0006)
    for z in (-.111,.111):
        box("Folded long wall",r,(.25,.192,.008),(0,0,z),carton,.0005)
        # Flaps fold inward down the outside, entirely inside the authored bounds.
        box("Folded lip",r,(.246,.029,.003),(0,.079,z+.003*(1 if z>0 else -1)),tape,.0005)
    for x in (-.121,.121):
        box("Folded end wall",r,(.008,.192,.214),(x,0,0),carton,.0005)
        box("Hand slot printed recess",r,(.001,.012,.061),(x+(.004 if x>0 else -.004),.052,0),ink,.003)
    accent=material("Carton "+key+" category",tone,.87)
    box("Category label",r,(.155,.060,.001),(0,.007,.115),ivory,.0004)
    box("Category band",r,(.155,.009,.001),(0,-.018,.116),accent,.0003)
    text("Printed "+key,r,label,(0,.011,.117),.014)
    # Recognizable category pictograms: chair, lamp, mug, and playful star.
    icon=empty(r.name+"_pictogram",r,p=(0,.041,.118))
    if key=="furniture":
        for x in (-.009,.009):
            rod("Chair leg icon",icon,(x,-.014,0),(x,0,0),.0013,.0013,ink)
        rod("Chair seat icon",icon,(-.014,0,0),(.014,0,0),.0015,.0015,ink)
        rod("Chair back icon",icon,(-.009,0,0),(-.009,.014,0),.0013,.0013,ink)
        rod("Chair rail icon",icon,(-.009,.014,0),(.010,.014,0),.0013,.0013,ink)
    elif key=="light":
        rod("Lamp stem icon",icon,(0,-.015,0),(0,.005,0),.0013,.0013,ink)
        rod("Lamp foot icon",icon,(-.010,-.015,0),(.010,-.015,0),.0015,.0015,ink)
        for a,b in [((-.014,.002,0),(-.008,.014,0)),((-.008,.014,0),(.008,.014,0)),
                    ((.008,.014,0),(.014,.002,0)),((-.014,.002,0),(.014,.002,0))]:
            rod("Lamp shade icon",icon,a,b,.0012,.0012,ink)
    elif key=="home":
        for a,b in [((-.011,.012,0),(-.011,-.009,0)),((-.011,-.009,0),(.009,-.009,0)),
                    ((.009,-.009,0),(.009,.012,0)),((-.011,.012,0),(.009,.012,0))]:
            rod("Cup icon",icon,a,b,.0013,.0013,ink)
        tube("Cup handle icon",icon,[(.010,.009,0),(.017,.007,0),(.017,-.003,0),(.010,-.005,0)],.0012,ink)
    else:
        pts=[(.017*math.sin(n*math.tau/5),.017*math.cos(n*math.tau/5),0) for n in range(5)]
        for n in range(5):rod("Joy star icon",icon,pts[n],pts[(n+2)%5],.0011,.0011,ink)
    for n in range(12):
        dot=empty(r.name+"_tally_"+str(n),r,p=(-.060+n*.011,-.007,.118))
        sphere("Inventory ink dot",dot,(.0022,.0022,.0005),(0,0,0),ink)
    for x in (-.098,.098):
        box("Vertical packing strip",r,(.015,.188,.0008),(x,-.002,.115),tape,.0002)
    fill=empty(r.name+"_fill",r,p=(0,-.09,0))
    for n in range(10):
        # Crumpled wrapping sheets with real folds and a thin paper edge.
        x=(n%3-1)*.061;z=(n//3-1.5)*.042
        points=[];faces=[];steps=8
        for row in range(steps+1):
            for col in range(steps+1):
                u=col/steps;v=row/steps
                fold=.012*abs(math.sin(u*8.2+n))+.011*math.sin(v*8.7+u*3+n*.8)
                edge=.009*(abs(u-.5)+abs(v-.5))
                points.append((x+(u-.5)*.061,.033+fold+edge+(n%3)*.007,z+(v-.5)*.051))
        for row in range(steps):
            for col in range(steps):
                a=row*(steps+1)+col;faces.append((a,a+steps+1,a+steps+2,a+1))
        packet=mesh("Folded wrapping tissue",fill,points,faces,ivory)
        bpy.context.view_layer.objects.active=packet
        mod=packet.modifiers.new("Thin cotton paper","SOLIDIFY");mod.thickness=.00018
        bpy.ops.object.modifier_apply(modifier=mod.name)

# Continuous entry parquet, same elevation as original floor, a welcome threshold
# and a right-side open door frame which leaves the hand path unobstructed.
r=root("entryway")
box("Entry walnut foundation",r,(.86,.06,.63),(.668,-.358,.915),walnut,.007)
atelier.parquet(r,(.25,1.086,.603,1.233))
box("Flush door threshold",r,(.044,.008,.34),(1.074,-.324,.909),brass,.0006)
for z in (.724,1.094):
    box("Door jamb",r,(.040,.65,.029),(1.074,.005,z),cream,.002)
box("Door lintel",r,(.044,.036,.40),(1.074,.343,.909),cream,.002)
# Door is attached to the outer frame and swings outward clear of playable floor.
door=empty("Entry open door",r,p=(1.074,.002,1.077))
door.rotation_euler.z=math.radians(-68)
box("Sage door panel",door,(.023,.634,.337),(0,0,-.1685),sage,.003)
for y in (-.11,.13):
    box("Door inset panel",door,(.026,.17,.25),(0,y,-.1685),linen,.002)
rod("Door brass handle",door,(.012,.015,-.29),(.038,.015,-.29),.006,.006,brass)
for y in (-.21,.21):
    rod("Attached brass hinge",r,(1.074,y-.018,1.077),(1.074,y+.018,1.077),.004,.004,brass)
box("Welcome woven mat",r,(.25,.006,.29),(.905,-.316,.91),sage,.003)

# Fire lives within the existing left-facing hearth cavity.
r=root("room_pendant",(.45,.55,-.55))
rod("Ceiling brass suspension",r,(0,.23,0),(0,.008,0),.002,.002,brass)
lathe("Linen ceiling shade",r,[(.081,-.02),(.082,-.01),(.058,.045),(.05,.055)],linen,pleats=.001)
bulb=empty("room_pendant_bulb",r)
lathe("Opal ceiling bulb",bulb,[(0,-.024),(.020,-.021),(.023,-.006),(.016,.010),(0,.015)],glow)

r=root("hearth_fire",(-1.018,-.32,.29))
for n,z in enumerate((-.07,-.02,.045,.078)):
    rod("Charred split log",r,(.035,.039,z-.018),(.108,.042,z+.018),.012,.011,black)
    for j in range(4):
        box("Orange ember",r,(.009,.003,.006),(.041+j*.017,.052,z),embermat,.001)
for n in range(7):
    p=(.055+(n%2)*.026,.046,(n-3)*.021)
    f=empty("hearth_flame_"+str(n),r,p=p)
    # Closed sculpted flame with bending tapered rings, soft varied silhouette.
    verts=[];faces=[]
    rings=[(0,.012),(.023,.018),(.051,.014),(.079,.008),(.110,.0008)]
    for j,(y,rad) in enumerate(rings):
        for k in range(10):
            a=k*math.tau/10
            verts.append((math.cos(a)*rad+math.sin(j*.7+n)*j*.0018,y,math.sin(a)*rad))
    for j in range(4):
        for k in range(10):
            a=j*10+k;b=j*10+(k+1)%10
            faces.append((a,b,b+10,a+10))
    faces.extend([tuple(range(9,-1,-1)),tuple(range(40,50))])
    mesh("Sculpted flame",f,verts,faces,heart if n%3==0 else flame)

r=root("shoecabinet")
box("Solid oak cabinet",r,(.28,.191,.13),(0,.0105,0),oak,.003)
for x in (-.114,.114):
    for z in (-.046,.046): rod("Tapered cabinet foot",r,(x,-.120,z),(x,-.078,z),.007,.006,walnut)
for x in (-.069,.069):
    box("Inset walnut cabinet door",r,(.133,.175,.003),(x,.014,.067),walnut,.0015)
    box("Recessed oak cabinet panel",r,(.113,.151,.0015),(x,.014,.069),oak,.001)
    for dx in (-.061,.061):
        box("Cabinet stile",r,(.007,.171,.002),(x+dx,.014,.069),walnut,.001)
    rod("Brass cabinet pull",r,(x-.017,.060,.071),(x+.017,.060,.071),.002,.002,brass)
box("Cabinet top edge",r,(.28,.014,.13),(0,.113,0),oak,.002)

r=root("coatrack")
lathe("Weighted oak foot",r,[(.066,-.21),(.075,-.205),(.069,-.194),(.024,-.185)],oak)
rod("Turned coat stem",r,(0,-.198,0),(0,.194,0),.009,.006,oak)
for n in range(4):
    a=n*math.tau/4
    rod("Branch hook",r,(0,.100,0),(.065*math.cos(a),.168,.065*math.sin(a)),.004,.003,oak)
lathe("Rounded top finial",r,[(0,.190),(.009,.192),(.01,.201),(0,.210)],brass)

r=root("sidetable")
lathe("Round solid oak top",r,[(0,.096),(.083,.096),(.085,.104),(.082,.110),(0,.110)],oak)
lathe("Lower shelf",r,[(0,-.061),(.060,-.061),(.060,-.054),(0,-.054)],oak)
for n in range(3):
    a=n*math.tau/3
    rod("Tapered side table leg",r,(.063*math.cos(a),-.110,.063*math.sin(a)),
        (.054*math.cos(a),.099,.054*math.sin(a)),.005,.004,walnut)

r=root("tablelamp")
lathe("Celadon lamp base",r,[(0,-.09),(.035,-.09),(.037,-.084),(.032,-.068),
    (.023,-.033),(.019,-.013),(.012,-.001)],ceramic)
rod("Brass lamp neck",r,(0,-.009,0),(0,.051,0),.004,.004,brass)
lathe("Pleated linen lampshade",r,[(.048,.007),(.048,.010),(.031,.087),(.029,.090),
    (.028,.086),(.045,.010)],linen,segments=192,pleats=.0008)
bulb=empty("tablelamp_bulb",r)
lathe("Warm opal bulb",bulb,[(0,.010),(.012,.012),(.014,.023),(.011,.035),(0,.038)],glow)

r=root("wallclock")
# Clock model faces +Z; its flat rear aligns with a wall.
face=lathe("Clock oak rim",r,[(0,-.0125),(.065,-.0125),(.07,-.009),
    (.07,.009),(.065,.0125),(.057,.0125),(.055,.009)],oak)
face.rotation_euler.x=math.pi/2
box("Ivory dial",r,(.112,.112,.002),(0,0,.010),ivory,.045)
for n in range(12):
    a=n*math.tau/12
    rod("Hour mark",r,(.049*math.sin(a),.049*math.cos(a),.012),
        (.055*math.sin(a),.055*math.cos(a),.012),.001,.001,ink)
rod("Clock hour hand",r,(0,0,.012),(0,.026,.012),.0015,.001,ink)
rod("Clock minute hand",r,(0,0,.012),(.034,-.020,.012),.001,.0007,brass)

r=root("blanket")
tailored_pad("Folded woven blanket",r,(.15,.053,.10),(0,0,0),claycloth)
for n in range(13):
    x=-.069+n*.011
    tube("Soft blanket fringe",r,[(x,-.008,.040),(x+.002,-.021,.046),(x,-.025,.049)],.0008,claycloth)
for y in (-.018,-.003,.013):
    tube("Folded blanket edge",r,[(-.065,y,.048),(0,y,.049),(.065,y,.048)],.0005,piping)

r=root("ottoman")
lathe("Round walnut footstool base",r,[(0,-.023),(.085,-.023),(.090,-.018),(.087,-.013),(0,-.013)],walnut)
lathe("Boucle round cushion",r,[(0,-.0175),(.072,-.0175),(.086,-.010),
    (.094,.004),(.095,.032),(.091,.054),(.080,.065),(0,.0675)],linen)
for x in (-.058,.058):
    for z in (-.058,.058):
        rod("Turned brass capped foot",r,(x,-.0675,z),(x,-.0175,z),.009,.007,walnut)
        rod("Brass foot collar",r,(x,-.0675,z),(x,-.060,z),.009,.009,brass)
for y in (-.004,.056):
    tube("Tailored cushion welt",r,[(.090*math.sin(n*math.tau/48),y,.090*math.cos(n*math.tau/48))
        for n in range(49)],.0012,piping)

r=root("gardenbench")
for n in range(5):
    box("Rounded teak seat slat",r,(.34,.025,.026),(0,-.005,-.057+n*.0285),oak,.002)
for y in (.031,.064,.097):
    box("Garden backrest slat",r,(.34,.029,.018),(0,y,-.0635),oak,.002)
for x in (-.142,.142):
    for z in (-.055,.055):
        rod("Splayed garden bench leg",r,(x,-.1175,z),(x*.95,-.009,z*.90),.0075,.0065,walnut)
    rod("Backrest upright",r,(x,-.055,-.0635),(x,.1115,-.0635),.006,.006,walnut)
    for z in (-.055,.055):
        sphere("Recessed brass joinery pin",r,(.002,.002,.001),(x,.008,z),brass)

# House fixtures are permanent architecture, not another copy in the goods pool.
r=root("house_clock",(.84,.39,-.948))
rim=lathe("Turned warm oak clock",r,[(0,-.011),(.126,-.011),(.135,-.006),
    (.135,.008),(.130,.017),(.116,.018),(.112,.012)],oak)
rim.rotation_euler.x=math.pi/2
face=lathe("Round ivory clock face",r,[(0,.012),(.116,.012),(.116,.015),(0,.015)],ivory)
face.rotation_euler.x=math.pi/2
for n in range(24):
    a=n*math.tau/24
    rod("Etched hour",r,(.100*math.sin(a),.100*math.cos(a),.016),
        ((.110 if n%6 else .113)*math.sin(a),(.110 if n%6 else .113)*math.cos(a),.016),
        .0008 if n%6 else .0013,.0008,ink)
for n in (0,6,12,18):
    a=n*math.tau/24
    text("Clock number "+str(n),r,str(n),(.083*math.sin(a),.083*math.cos(a),.016),.017)
# Sunrise and night symbols carved into the dial.
sphere("Clock sun",r,(.009,.009,.001),(0,-.061,.016),brass)
for n in range(8):
    a=n*math.tau/8
    rod("Clock sun ray",r,(.013*math.sin(a),-.061+.013*math.cos(a),.016),
        (.018*math.sin(a),-.061+.018*math.cos(a),.016),.0007,.0007,brass)
tube("Clock moon",r,[(.010,-.0+.054,.016),(.003,.049,.016),(-.007,.052,.016),
    (-.012,.060,.016),(-.008,.070,.016),(.002,.073,.016)],.0025,ink)
needle=empty("house_clock_needle",r,p=(0,0,.020))
rod("Brass time pointer",needle,(0,-.013,0),(0,.088,0),.0025,.0012,brass)
sphere("Pointer hub",needle,(.007,.007,.003),(0,0,.001),brass)

r=root("house_dimmer",(.84,.15,-.948))
box("Porcelain dimmer plate",r,(.112,.098,.011),(0,0,0),ceramic,.025)
knob=empty("house_dimmer_knob",r,p=(0,0,.014))
dial=lathe("Fluted porcelain knob",knob,[(0,-.004),(.030,-.004),(.033,.003),
    (.032,.016),(.028,.020),(0,.020)],ivory,pleats=.001)
dial.rotation_euler.x=math.pi/2
rod("Brass dimmer notch",knob,(0,.012,.022),(0,.025,.022),.0012,.0012,brass)
for n in range(7):
    a=math.radians(-135+n*45)
    sphere("Dimmer tick",r,(.0014,.0014,.0007),(.042*math.sin(a),.042*math.cos(a),.008),ink)

# Both architectural apertures remain genuinely open; no scenery backplates.

# Bake the expanded architecture. Furniture and fixture joinery stay at their
# authored size, while flooring and window spans cover the larger footprint.
EXPANSION=1.45
def move_xz(o):
    o.location.x*=EXPANSION;o.location.y*=EXPANSION
def stretch_xz(o):
    move_xz(o)
    if o.type=="MESH":
        for v in o.data.vertices:
            v.co.x*=EXPANSION;v.co.y*=EXPANSION
        for uv in (o.data.uv_layers.active.data if o.data.uv_layers.active else []):
            uv.uv*=EXPANSION
for name,r in roots.items():
    if name=="entryway":
        for o in r.children:
            if o.name.startswith(("Entry walnut","Entry parquet","Herringbone oak")): stretch_xz(o)
            elif o.name.startswith(("Door jamb","Door lintel","Flush door","Attached brass","Entry open door")):
                # Expand the entry, keeping door/frame/hinge distances identical.
                o.location.x+=(EXPANSION-1)*1.074
                o.location.y-=(EXPANSION-1)*.909
            else: move_xz(o)
    elif name.startswith(("house_","room_pendant","hearth_fire")):
        move_xz(r)

# Merge only immediate static geometry; dynamic fill and flame pivots survive.
atelier.apply()
for parent in [o for o in bpy.data.objects if o.type=="EMPTY"]:
    children=[o for o in parent.children if o.type=="MESH"]
    if not children: continue
    bpy.ops.object.select_all(action="DESELECT")
    for o in children:o.select_set(True)
    bpy.context.view_layer.objects.active=children[0]
    if len(children)>1:bpy.ops.object.join()
    o=children[0];o.name=parent.name+"_mesh"
    bpy.ops.object.transform_apply(location=False,rotation=True,scale=True)
    mod=o.modifiers.new("Triangles","TRIANGULATE");bpy.ops.object.modifier_apply(modifier=mod.name)
bpy.context.view_layer.update()
manifest={}
for name,r in roots.items():
    coords=[r.matrix_world.inverted()@c.matrix_world@Vector(p)
        for c in r.children_recursive if c.type=="MESH" for p in c.bound_box]
    points=[(p.x,p.z,-p.y) for p in coords]
    manifest[name]={"min":[min(p[k] for p in points) for k in range(3)],
        "max":[max(p[k] for p in points) for k in range(3)]}
(OUT/"manifest.json").write_text(json.dumps(manifest,indent=2))
bpy.ops.object.select_all(action="SELECT")
bpy.ops.export_scene.gltf(filepath=str(ASSETS/"tidy-moving-home.glb"),export_format="GLB",
    export_yup=True,export_apply=True,export_tangents=True,export_animations=False)
if "--export-only" in sys.argv:
    print("TIDY_MOVING_HOME_EXPORT_COMPLETE",flush=True)
    sys.exit(0)

# Inspection contact sheet is an offline art view, separate from runtime evidence.
for name,r in roots.items():
    if name.startswith(("entryway","hearth_fire","room_pendant","house_")):
        r.hide_render=True
        for child in r.children_recursive:child.hide_render=True
for i,key in enumerate(("furniture","light","home","toys")):
    roots["movingbox-"+key].location=xyz((-.48+i*.32,.10,-.18))
for i,name in enumerate(("shoecabinet","coatrack","sidetable","tablelamp","wallclock","blanket","ottoman","gardenbench")):
    roots[name].location=xyz((-.75+i*.23,-manifest[name]["min"][1],.20))
box("Studio floor",None,(2.2,.018,1.0),(0,-.012,0),paper,.003)
world=bpy.context.scene.world;world.use_nodes=True
world.node_tree.nodes["Background"].inputs[0].default_value=(.65,.70,.78,1)
world.node_tree.nodes["Background"].inputs[1].default_value=.35
for p,power,size in [((-.5,1.2,.5),100,1),((.7,.7,-.5),70,.8)]:
    light=bpy.data.lights.new("Studio","AREA");light.energy=power;light.size=size
    o=bpy.data.objects.new("Studio",light);bpy.context.collection.objects.link(o)
    o.location=xyz(p);o.rotation_euler=(Vector(xyz((0,.1,0)))-o.location).to_track_quat("-Z","Y").to_euler()
camera=bpy.data.objects.new("Inspection",bpy.data.cameras.new("Inspection"))
bpy.context.collection.objects.link(camera);camera.location=xyz((.8,1.25,1.9))
camera.rotation_euler=(Vector(xyz((0,.10,0)))-camera.location).to_track_quat("-Z","Y").to_euler()
camera.data.type="ORTHO";camera.data.ortho_scale=2.35
scene=bpy.context.scene;scene.camera=camera;scene.render.engine="CYCLES"
scene.cycles.samples=32;scene.cycles.use_denoising=True
scene.render.resolution_x=1600;scene.render.resolution_y=1000;scene.render.resolution_percentage=100
scene.view_settings.view_transform="AgX";scene.render.filepath=str(OUT/"blender-inspection.png")
atelier.warm_preview(scene)
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/"TidySpace-moving-home.blend"))
bpy.ops.render.render(write_still=True)
print("TIDY_MOVING_HOME_COMPLETE",flush=True)
