"""Thirty distinct home/toy assets. Native metres, +Z front, centred root, shared PBR.

Blender --background --python scene/build_home_expansion.py
The animated empties below are the runtime contract; never merge across pivots.
"""
import bpy
import json
import math
import re
import sys
from pathlib import Path
from mathutils import Vector

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import art_direction as atelier

OUT = HERE / "home-expansion"
OUT.mkdir(exist_ok=True)
ASSETS = HERE.parent / "app/src/main/assets/models"
bpy.ops.object.select_all(action="SELECT")
bpy.ops.object.delete(use_global=False)
source = (HERE.parent / "app/src/main/java/com/example/tidyspace/pc5853b2a/domain/model/HomeExpansion.kt").read_text()
specs = {m[0]: (m[1], tuple(float(v.rstrip("f")) for v in m[2:])) for m in
         re.findall(r'extra\("([^"]+)","([^"]+)",Furnishing\.\w+,([\d.]+f),([\d.]+f),([\d.]+f)\)', source)}

def xyz(p): return (p[0], -p[2], p[1])
def empty(name, parent=None, p=(0, 0, 0)):
    o = bpy.data.objects.new(name, None)
    bpy.context.collection.objects.link(o)
    o.parent = parent
    o.location = xyz(p)
    return o
def finish(o, name, parent, material):
    o.name = name
    o.parent = parent
    o.data.materials.append(material)
    for face in o.data.polygons: face.use_smooth = True
    return o
def box(name, parent, size, p, material, bevel=.003):
    bpy.ops.mesh.primitive_cube_add(size=1, location=xyz(p))
    o = bpy.context.object
    o.dimensions = (size[0], size[2], size[1])
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    mod = o.modifiers.new("Rounded joinery", "BEVEL")
    mod.width = min(bevel, min(size)*.44)
    mod.segments = 3
    bpy.ops.object.modifier_apply(modifier=mod.name)
    return finish(o, name, parent, material)
def ball(name, parent, size, p, material):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=24, ring_count=16, radius=1, location=xyz(p))
    o = bpy.context.object
    o.scale = (size[0]/2, size[2]/2, size[1]/2)
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    return finish(o, name, parent, material)
def rod(name, parent, a, b, radius, material, vertices=16):
    delta = Vector(xyz(b))-Vector(xyz(a))
    bpy.ops.mesh.primitive_cylinder_add(vertices=vertices, radius=radius, depth=delta.length,
                                      location=(Vector(xyz(a))+Vector(xyz(b)))/2)
    o = bpy.context.object
    o.rotation_mode = "QUATERNION"
    o.rotation_quaternion = delta.to_track_quat("Z", "Y")
    return finish(o, name, parent, material)
def line(name, parent, points, radius, material):
    for a, b in zip(points, points[1:]): rod(name, parent, a, b, radius, material)
def lathe(name, parent, profile, material, p=(0, 0, 0), ellipse=1):
    n = 40
    verts = [xyz((p[0]+r*math.cos(i*math.tau/n), p[1]+h,
                  p[2]+r*math.sin(i*math.tau/n)*ellipse)) for r, h in profile for i in range(n+1)]
    faces = [(j*(n+1)+i, (j+1)*(n+1)+i, (j+1)*(n+1)+i+1, j*(n+1)+i+1)
             for j in range(len(profile)-1) for i in range(n)]
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata(verts, [], faces)
    mesh.update()
    o = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(o)
    uv = mesh.uv_layers.new()
    for f in mesh.polygons:
        for loop in f.loop_indices:
            v = mesh.loops[loop].vertex_index
            uv.data[loop].uv = ((v % (n+1))/n, (v//(n+1))/(len(profile)-1))
    return finish(o, name, parent, material)
def ring(name, parent, radius, tube, p, material, front=False, ellipse=1):
    profile = [(radius+tube*math.cos(i*math.tau/12), tube*math.sin(i*math.tau/12)) for i in range(13)]
    o = lathe(name, parent, profile, material, ellipse=ellipse)
    if front: o.rotation_euler.x = math.pi/2
    o.location = xyz(p)
    return o
def footset(r, w, d, bottom, top, material, radius=.009):
    for x in (-w/2, w/2):
        for z in (-d/2, d/2): rod("Turned leg", r, (x,bottom,z), (x,top,z), radius, material)
def handle(r, x, y, z, length=.04, vertical=False, material=None):
    material = material or brass
    ends = [(x, y-length/2, z), (x, y+length/2, z)] if vertical else [(x-length/2,y,z),(x+length/2,y,z)]
    for a in ends: rod("Handle mount", r, (a[0],a[1],z-.007), a, .0025, material)
    rod("Brass pull", r, *ends, .0027, material)
def panel(r, size, p, material, inset):
    box("Raised panel frame", r, size, p, material)
    box("Inset field", r, (size[0]-.016,size[1]-.016,.003), (p[0],p[1],p[2]+size[2]/2+.0005), inset,.001)

oak = atelier.pbr("Honey oak", "wood", "A47B52", .52)
walnut = atelier.pbr("Walnut", "wood", "70503A", .52)
cream = atelier.pbr("Warm ivory enamel", "glaze", "DED3BE", .29)
jade = atelier.pbr("Sage ceramic", "glaze", "758267", .3)
brass = atelier.pbr("Satin aged brass", "metal", "B59452", .36, .8)
iron = atelier.pbr("Warm charcoal iron", "metal", "393A32", .57, .28)
linen = atelier.pbr("Natural linen", "cloth", "C9B89B", .9)
slate = atelier.pbr("Slate textile", "cloth", "496777", .88)
olive = atelier.pbr("Olive painted timber", "wood", "646B39", .6)
terra = atelier.pbr("Terracotta", "plain", "A85D3E", .82)
paper = atelier.pbr("Ivory paper", "plain", "C9B89B", .9)
leafmat = atelier.pbr("Botanical leaf", "plain", "4B663E", .55)
rubber = atelier.pbr("Soft charcoal rubber", "plain", "343932", .84)
glass = atelier.pbr("Smoked glass", "glaze", "33464A", .16, .12)
golden = atelier.pbr("Warm sand", "plain", "B88837", .78)
roots = {k: empty(k) for k in specs}

# Bedroom: wide headboard, tucked duvet, separately piped pillows.
r = roots["bed"]
box("Solid oak bed frame",r,(.52,.065,.66),(0,-.065,0),oak)
box("Mattress ticking",r,(.49,.06,.60),(0,-.005,.03),linen,.018)
box("Tailored blue duvet",r,(.487,.020,.415),(0,.025,.119),slate,.009)
box("Folded linen cuff",r,(.487,.011,.058),(0,.036,-.059),linen,.005)
box("Headboard",r,(.52,.24,.025),(0,.02,-.3275),oak,.006)
for x in (-.124,.124):
    panel(r,(.223,.174,.009),(x,.025,-.310),oak,walnut)
    box("Plump pillow",r,(.197,.050,.125),(x*.86,.060,-.20),linen,.020)
    line("Pillow piping",r,[(x*.86-.082,.062,-.256),(x*.86+.082,.062,-.256),
         (x*.86+.088,.062,-.144),(x*.86-.088,.062,-.144),(x*.86-.082,.062,-.256)],.0014,cream)
footset(r,.45,.57,-.14,-.057,walnut,.0125)
for x in (-.23,.23): rod("Duvet seam",r,(x,.030,-.06),(x,.030,.31),.001,linen)

def cabinet(r,w,h,d,drawers=False):
    box("Case",r,(w-.01,h-.04,d-.014),(0,.01,-.007),oak)
    box("Moulded top",r,(w,.016,d),(0,h/2-.008,0),walnut)
    box("Base plinth",r,(w,.016,d),(0,-h/2+.036,0),walnut)
    footset(r,w-.047,d-.044,-h/2,-h/2+.04,walnut,.01)
    if drawers:
        height=(h-.07)/3
        for n in range(3):
            y=-h/2+.043+height*(n+.5)
            panel(r,(w-.026,height-.006,.014),(0,y,d/2-.008),oak,walnut)
            for x in (-w*.24,w*.24): handle(r,x,y,d/2+.003,length=.035)
    else:
        for sign in (-1,1):
            x=sign*w*.237
            panel(r,(w*.455,h-.070,.012),(x,.008,d/2-.009),oak,olive)
            for n in range(18):
                y=-h/2+.075+n*(h-.155)/17
                o=box("Louvred timber slat",r,(w*.365,.014,.009),(x,y,d/2+.0005),oak,.001)
                o.rotation_euler.x=.22
            handle(r,sign*.018,.012,d/2+.005,.047,True)
cabinet(roots["wardrobe"],.32,.60,.22)
cabinet(roots["dresser"],.34,.28,.18,True)

# Dining suite and living-room storage.
r=roots["diningtable"]
lathe("Elliptical oak table",r,[(0,.128),(.202,.128),(.210,.132),(.210,.146),(.206,.150),(0,.150)],oak,ellipse=.30/.42)
footset(r,.28,.16,-.15,.13,walnut,.011)
for x in (-.14,.14): box("Table apron",r,(.018,.044,.19),(x,.109,0),oak)
r=roots["diningchair"]
box("Seat frame",r,(.17,.021,.17),(0,-.0265,.005),oak)
box("Woven seat cushion",r,(.145,.010,.142),(0,-.010,.01),linen)
for x in (-.073,.073): rod("Back post",r,(x,-.026,-.078),(x,.132,-.078),.007,oak)
for y in (.006,.130): rod("Curved back rail",r,(-.075,y,-.078),(.075,y,-.078),.008,oak)
for x in range(-6,7): rod("Cane upright",r,(x*.010,.014,-.078),(x*.010,.121,-.078),.0017,linen)
for n in range(10): rod("Cane crossweave",r,(-.066,.017+n*.011,-.076),(.066,.017+n*.011,-.076),.0016,linen)
footset(r,.13,.134,-.14,-.024,walnut,.008)
r=roots["tvconsole"]
box("Console frame",r,(.46,.147,.16),(0,.0135,0),walnut)
box("Oak crown",r,(.46,.013,.16),(0,.0935,0),oak)
footset(r,.40,.115,-.10,-.055,brass,.008)
for x in (-.153,.153):
    panel(r,(.138,.126,.012),(x,.015,.077),walnut,olive)
    handle(r,x,.015,.085,.035)
box("Shadow in media niche",r,(.145,.12,.006),(0,.015,.081),iron)
box("Niche shelf",r,(.14,.008,.025),(0,-.002,.070),oak)
for n in range(4): box("Record sleeves",r,(.012,.047,.01),(-.036+n*.02,-.030,.083),(terra,slate,linen,olive)[n])
r=roots["television"]
box("Walnut television casing",r,(.30,.199,.068),(0,.012,0),walnut,.014)
box("Ivory bevel",r,(.224,.166,.008),(-.025,.013,.034),cream,.011)
box("Curved CRT glass",r,(.201,.142,.010),(-.025,.013,.038),glass,.026)
for y in (-.025,.036):
    rod("Tuning dial",r,(.121,y,.033),(.121,y,.042),.012,brass,32)
    box("Dial marker",r,(.002,.010,.002),(.121,y+.004,.043),cream,.0005)
for n in range(6): box("Speaker vent",r,(.030,.003,.003),(.121,-.055+n*.005,.037),iron,.001)
for x in (-.105,.105): rod("Television foot",r,(x,-.115,.013),(x*.86,-.081,.0),.006,iron)
r=roots["bookcase"]
for y in (-.254,-.13,-.006,.118,.254): box("Open oak shelf",r,(.26,.012,.13),(0,y,0),oak)
for x in (-.124,.124): box("Tall side stile",r,(.012,.52,.13),(x,0,0),walnut)
box("Recessed boarded back",r,(.24,.50,.009),(0,0,-.0605),olive)
for x in (-.08,0,.08): box("Backboard seam",r,(.001,.50,.001),(x,0,-.055),walnut,.0003)

# Kitchen and appliances. All controls and doors visibly face +Z.
def appliance_body(r,w,h,d,color=cream):
    box("Enamel body",r,(w,h-.012,d),(0,.006,0),color,.009)
    box("Recessed dark plinth",r,(w-.028,.015,d-.020),(0,-h/2+.0075,0),iron,.002)
def basin(r,w,d,y,material):
    # Open, thick-walled vessel: exterior and interior profiles meet at a rolled rim.
    lathe("Open ceramic basin",r,[(0,y-.045),(w*.31,y-.045),(w*.46,y-.015),
          (w*.50,y),(w*.48,y+.003),(w*.43,y-.006),(w*.29,y-.035),(0,y-.035)],material,ellipse=d/w)
    rod("Drain",r,(0,y-.034,0),(0,y-.031,0),.006,brass)
def faucet(r,x,y,z):
    line("Gooseneck faucet",r,[(x,y,z),(x,y+.045,z),(x,y+.055,z+.012),(x,y+.055,z+.038),(x,y+.046,z+.044)],.0035,brass)
    for a in (-.015,.015): rod("Tap handle",r,(x+a,y+.012,z),(x+a,y+.026,z),.0025,brass)
def open_vanity(r,w,d,bottom,top,color):
    box("Cabinet back",r,(w,top-bottom,.012),(0,(top+bottom)/2,-d/2+.006),color)
    for x in (-w/2+.006,w/2-.006):
        box("Cabinet side",r,(.012,top-bottom,d),(x,(top+bottom)/2,0),color)
    box("Cabinet floor",r,(w,.014,d),(0,bottom+.007,0),color)
    for x in (-w*.24,w*.24):
        panel(r,(w*.45,top-bottom-.016,.010),(x,(top+bottom)/2,d/2-.005),color,color)
        handle(r,x,top-.045,d/2+.006,.035)
    # An elliptical cutout fits under the rolled basin rim without open corners.
    verts=[];count=64
    for n in range(count):
        a=n*math.tau/count;c=math.cos(a);s=math.sin(a)
        radius=min(w/2/max(abs(c),1e-6),d/2/max(abs(s),1e-6))
        verts.extend([xyz((c*radius,top+.014,s*radius)),
                      xyz((c*.084,top+.014,s*.065))])
    faces=[(2*n,2*n+1,2*((n+1)%count)+1,2*((n+1)%count)) for n in range(count)]
    mesh=bpy.data.meshes.new("Oval-cut stone counter");mesh.from_pydata(verts,[],faces);mesh.update()
    o=bpy.data.objects.new("Stone countertop",mesh);bpy.context.collection.objects.link(o)
    finish(o,o.name,r,cream)
    mod=o.modifiers.new("Stone slab thickness","SOLIDIFY");mod.thickness=.014
    bpy.context.view_layer.objects.active=o;bpy.ops.object.modifier_apply(modifier=mod.name)
r=roots["sink"]
open_vanity(r,.34,.22,-.1275,.108,olive)
basin(r,.188,.150,.120,cream)
faucet(r,0,.120,-.082)
# Sink height includes its tap; lower cabinetry grounds at the declared bottom.
for o in r.children: o.location.z-=.0275
footset(r,.29,.17,-.155,-.14,iron,.008)
r=roots["stove"]
appliance_body(r,.25,.31,.23)
box("Hob",r,(.241,.008,.224),(0,.148,0),iron)
for x in (-.064,.064):
    for z in (-.058,.058):
        ring("Gas burner",r,.035,.003,(x,.155,z),iron)
        rod("Burner cap",r,(x,.151,z),(x,.155,z),.022,brass,24)
        for a in (0,math.pi/2):
            rod("Pan support",r,(x-math.cos(a)*.043,.157,z-math.sin(a)*.043),
                (x+math.cos(a)*.043,.157,z+math.sin(a)*.043),.0025,iron)
box("Oven door frame",r,(.222,.176,.010),(0,-.042,.115),iron)
box("Oven glass",r,(.193,.137,.007),(0,-.048,.122),glass,.007)
handle(r,0,.060,.125,.17)
for x in (-.084,-.028,.028,.084): rod("Hob knob",r,(x,.109,.115),(x,.109,.125),.010,brass,24)
r=roots["fridge"]
appliance_body(r,.22,.46,.21)
for y,h in ((.129,.182),(-.098,.255)):
    box("Rounded refrigerator door",r,(.214,h,.011),(0,y,.106),cream,.009)
    handle(r,-.074,y,.119,.079,True)
box("Vintage badge",r,(.042,.013,.003),(.041,.171,.114),brass,.003)
r=roots["washer"]
appliance_body(r,.24,.29,.24)
rod("Round door recess",r,(0,-.016,.118),(0,-.016,.124),.079,iron,40)
rod("Smoked laundry porthole",r,(0,-.016,.122),(0,-.016,.126),.063,glass,40)
ring("Chrome door bezel",r,.075,.006,(0,-.016,.127),brass,True)
handle(r,.070,-.016,.135,.035,True)
box("Detergent drawer",r,(.097,.030,.006),(-.051,.105,.121),cream)
rod("Program dial",r,(.067,.105,.12),(.067,.105,.131),.017,brass,32)
box("Programme label",r,(.048,.009,.002),(.007,.105,.126),iron)

# Bathroom with hollow vessels, not filled solid tubs.
r=roots["bathvanity"]
open_vanity(r,.28,.20,-.116,.117,slate)
basin(r,.190,.150,.129,cream)
faucet(r,.0,.130,-.080)
for o in r.children: o.location.z-=.015
footset(r,.236,.156,-.15,-.113,brass,.008)
r=roots["toilet"]
lathe("Pedestal",r,[(0,-.115),(.058,-.115),(.061,-.101),(.040,-.045),(.060,-.024),(0,-.024)],cream,p=(0,0,.035),ellipse=1.2)
lathe("Toilet bowl",r,[(0,-.034),(.057,-.027),(.080,.009),(.085,.048),(.081,.053),
      (.069,.050),(.062,.014),(.034,-.012),(0,-.012)],cream,p=(0,0,.037),ellipse=1.1)
ring("Ivory toilet seat",r,.076,.006,(0,.055,.037),linen,ellipse=1.12)
box("Cistern",r,(.16,.164,.057),(0,.027,-.100),cream,.012)
box("Cistern lid",r,(.166,.012,.062),(0,.109,-.100),cream,.004)
rod("Flush button",r,(.035,.116,-.100),(.035,.119,-.100),.009,brass,24)
r=roots["bathtub"]
lathe("Clawfoot bathtub",r,[(0,-.072),(.150,-.072),(.203,-.041),(.230,.035),(.24,.090),
      (.238,.10),(.228,.10),(.220,.084),(.198,-.022),(.144,-.049),(0,-.049)],cream,ellipse=.26/.48)
for x in (-.153,.153):
    for z in (-.069,.069):
        ball("Claw foot",r,(.043,.035,.030),(x,-.083,z),brass)
        rod("Foot ankle",r,(x,-.085,z),(x*.95,-.052,z*.9),.007,brass)
rod("Tub drain",r,(.130,-.047,0),(.130,-.044,0),.011,brass,24)
r=roots["mirror"]
# Flat arched glazing polygon and a continuous rounded brass surround.
outline=[(-.096,-.146),(.096,-.146),(.096,.049)]+[
    (.096*math.cos(a),.049+.096*math.sin(a)) for a in [i*math.pi/32 for i in range(33)]]
mesh=bpy.data.meshes.new("Arched mirror")
mesh.from_pydata([xyz((x,y,0)) for x,y in outline],[],[tuple(range(len(outline)))])
mesh.update()
o=bpy.data.objects.new("Smoked reflective glazing",mesh);bpy.context.collection.objects.link(o)
finish(o,o.name,r,glass)
line("Solid brass arch",r,[(x,y,.003) for x,y in outline+[outline[0]]],.004,brass)
box("Mirror back",r,(.19,.20,.007),(0,-.05,-.004),walnut)

# Household accessories.
r=roots["laundrybasket"]
lathe("Woven laundry basket",r,[(0,-.095),(.066,-.095),(.072,-.082),(.077,.074),(.079,.087),
      (.074,.092),(.070,.085),(.066,-.074),(0,-.074)],linen,ellipse=.88)
for n in range(16):
    y=-.080+n*.0105
    ring("Wicker horizontal",r,.072+(y+.080)*.035,.0015,(0,y,0),oak,ellipse=.88)
for n in range(24):
    a=n*math.tau/24
    rod("Wicker strand",r,(.070*math.cos(a),-.082,.061*math.sin(a)),
        (.079*math.cos(a),.087,.069*math.sin(a)),.0018,linen)
for sign in (-1,1):
    line("Basket handle",r,[(sign*.072,.060,-.025),(sign*.075,.087,-.025),
         (sign*.075,.090,.025),(sign*.072,.060,.025)],.003,oak)
r=roots["tallplant"]
lathe("Terracotta cachepot",r,[(0,-.190),(.050,-.190),(.056,-.184),(.067,-.090),
      (.070,-.083),(.065,-.079),(.060,-.092),(0,-.094)],terra)
rod("Rich potting soil",r,(0,-.095,0),(0,-.090,0),.061,iron,32)
def leaf(parent,tip,base,width,material):
    a=Vector(base);b=Vector(tip);delta=b-a
    side=delta.cross(Vector((0,1,.2))).normalized()*width
    vertices=[]
    # A curved, lobed outline with split edges and a central midrib.
    segments=14
    for n in range(segments+1):
        t=n/segments
        center=a+delta*t+Vector((0,math.sin(t*math.pi)*.012,0))
        fullness=math.sin(t*math.pi)**.75
        scallop=.66 if n in (3,5,7,9,11) else 1
        spread=side*fullness*scallop
        vertices.extend([center-spread,center+Vector((0,.002,0)),center+spread])
    faces=[]
    for n in range(segments):
        i=n*3
        faces.extend([(i,i+3,i+4,i+1),(i+1,i+4,i+5,i+2)])
    mesh=bpy.data.meshes.new("Botanical leaf")
    mesh.from_pydata([xyz(v) for v in vertices],[],faces)
    mesh.update()
    o=bpy.data.objects.new("Veined botanical leaf",mesh);bpy.context.collection.objects.link(o)
    finish(o,o.name,parent,material)
    mod=o.modifiers.new("Leaf thickness","SOLIDIFY");mod.thickness=.0008
    bpy.context.view_layer.objects.active=o;bpy.ops.object.modifier_apply(modifier=mod.name)
    rod("Leaf midrib",parent,base,tip,.0006,olive)
for n,(tip,base,width) in enumerate([
    ((-.078,.095,.008),(-.004,-.066,0),.036),((.067,.143,-.022),(.009,-.070,-.01),.035),
    ((-.015,.188,-.055),(0,-.065,0),.035),((.040,.054,.071),(.013,-.075,.008),.036),
    ((-.055,.018,-.063),(-.018,-.075,0),.036),((.072,-.002,-.033),(.011,-.077,-.006),.031),
    ((-.050,.144,.047),(-.006,-.074,.001),.028)]):
    stem=tuple(base[k]+(tip[k]-base[k])*.68 for k in range(3))
    rod("Plant petiole",r,base,stem,.0018,leafmat)
    leaf(r,tip,stem,width,leafmat)
r=roots["teapot"]
lathe("Celadon teapot",r,[(0,-.045),(.026,-.045),(.035,-.031),(.040,-.010),(.034,.017),
      (.024,.025),(.022,.022),(.026,.014),(.034,-.011),(.026,-.035),(0,-.035)],jade)
lathe("Lid",r,[(0,.030),(.027,.026),(.024,.022),(0,.022)],jade)
ball("Lid finial",r,(.014,.014,.014),(0,.035,0),brass)
line("Curved spout",r,[(.025,-.012,0),(.047,.006,0),(.055,.024,0)],.0075,jade)
ring("Teapot handle",r,.023,.0045,(-.034,.004,0),brass,True)
r=roots["fruitbowl"]
lathe("Fruit platter",r,[(0,-.035),(.035,-.035),(.068,-.020),(.075,-.002),(.073,.003),
      (.067,-.003),(.029,-.025),(0,-.025)],cream,ellipse=.80)
for x,y,z,m in [(-.029,-.002,-.013,terra),(.022,.001,-.014,golden),(0,.006,.020,leafmat)]:
    ball("Fresh orchard fruit",r,(.039,.044,.039),(x,y,z),m)
    rod("Fruit stem",r,(x,y+.019,z),(x+.002,y+.025,z),.0015,walnut)
r=roots["crockery"]
for n in range(3):
    y=-.0325+n*.007
    lathe("Stacked ceramic plate",r,[(0,y),(.032,y),(.055,y+.009),(.054,y+.012),
          (.033,y+.004),(0,y+.004)],cream)
lathe("Soup bowl",r,[(0,-.010),(.019,-.010),(.038,.016),(.037,.025),(.032,.024),
      (.026,.002),(0,-.005)],jade,p=(0,0,0))
for x in (-.060,.060):
    rod("Cutlery handle",r,(x,-.022,-.036),(x,-.022,.022),.002,brass)
    if x>0: ball("Spoon bowl",r,(.009,.002,.018),(x,-.021,.031),brass)
    else:
        for n in range(3): rod("Fork tine",r,(x+(n-1)*.002,-.022,.021),(x+(n-1)*.002,-.022,.042),.0007,brass)
r=roots["alarmclock"]
rod("Clock casing",r,(0,-.006,-.020),(0,-.006,.015),.037,brass,48)
rod("Ivory clock face",r,(0,-.006,.015),(0,-.006,.018),.033,cream,48)
for n in range(12):
    a=n*math.tau/12
    rod("Hour marker",r,(math.sin(a)*.027,-.006+math.cos(a)*.027,.019),
        (math.sin(a)*.030,-.006+math.cos(a)*.030,.019),.0008,iron)
line("Clock hour hand",r,[(0,-.006,.020),(-.011,.008,.020)],.0014,iron)
line("Clock minute hand",r,[(0,-.006,.021),(.015,.016,.021)],.001,iron)
for x in (-.031,.031):
    ball("Twin alarm bell",r,(.035,.018,.035),(x,.035,0),brass)
    rod("Clock leg",r,(x,-.0475,.003),(x*.7,-.031,.003),.0035,brass)
rod("Bell striker",r,(0,.028,0),(0,.044,0),.002,iron)
handle(r,0,.044,-.012,.024)
r=roots["books"]
for n,m in enumerate((slate,terra,olive)):
    y=-.026+n*.022
    p=empty("Book volume "+str(n),r,(0,y,0))
    p.rotation_euler.z=(n-1)*.055
    box("Paper edges",p,(.112,.017,.078),(0,0,0),paper,.001)
    for h in (-.010,.010): box("Cloth cover",p,(.12,.004,.085),(0,h,0),m,.001)
    box("Linen spine",p,(.008,.022,.085),(-.056,0,0),m,.002)
    for z in (-.023,.023): box("Gilt spine band",p,(.001,.018,.004),(-.061,0,z),brass,.0005)
r=roots["vase"]
lathe("Narrow-neck ceramic vase",r,[(0,-.08),(.020,-.080),(.028,-.070),(.027,-.036),
      (.015,-.017),(.012,.025),(.014,.032),(.010,.034),(.008,.027),(.009,-.025),
      (.018,-.044),(0,-.047)],jade)
for n,tip in enumerate(((-.032,.080,.016),(.031,.069,-.018),(.012,.080,.023))):
    line("Dry branch",r,[(0,-.035,0),((n-1)*.006,.026,0),tip],.0012,walnut)
    for sign in (-1,1):
        branch=((tip[0]+sign*.012)*.7,tip[1]*.77,tip[2]*.6)
        leaf(r,branch,(tip[0]*.6,tip[1]*.6,tip[2]*.6),.009,olive)

# Six genuinely different mechanisms; all child transforms match PlaythingAnimator.
r=roots["slider"]
box("Magnetic slider tray",r,(.15,.014,.065),(0,-.0155,0),walnut)
for z in (-.026,.026): box("Brass guide rail",r,(.138,.008,.004),(0,-.002,z),brass)
for n in range(5):
    rod("Magnetic detent",r,((n-2)*.022,-.007,0),((n-2)*.022,-.004,0),.004,brass)
p=empty("slider_thumb",r,(0,.006,0))
box("Fluted thumb block",p,(.042,.030,.047),(0,0,0),olive,.005)
for n in range(5): box("Grip ridge",p,(.031,.0014,.0014),(0,.015,(n-2)*.006),brass,.0005)
r=roots["togglebox"]
box("Toggle walnut housing",r,(.15,.032,.085),(0,-.0165,0),walnut)
for n in range(3):
    x=(n-1)*.045
    box("Switch recess",r,(.036,.005,.059),(x,.002,0),iron)
    p=empty("togglebox_"+str(n),r,(x,.003,0))
    box("Rocking switch",p,(.028,.024,.049),(0,0,0),(olive,terra,slate)[n])
    ring("Switch dimple",p,.004,.0008,(0,.012,.012),brass)
r=roots["abacus"]
for x in (-.074,.074): box("Abacus side",r,(.012,.11,.055),(x,0,0),walnut)
for y in (-.049,.049): box("Abacus end",r,(.16,.012,.055),(0,y,0),oak)
box("Bead divider",r,(.14,.006,.040),(0,.002,-.005),oak)
for n in range(5):
    x=(n-2)*.028
    rod("Counting spindle",r,(x,-.042,0),(x,.042,0),.0018,brass)
    p=empty("abacus_"+str(n),r,(x,-.023,0))
    lathe("Turned counting bead",p,[(0,-.011),(.007,-.011),(.012,-.006),(.012,.006),
          (.007,.011),(0,.011)],(olive,terra,slate,brass,oak)[n])
r=roots["bell"]
lathe("Bell foot",r,[(0,-.0425),(.043,-.0425),(.0475,-.034),(.043,-.026),(0,-.026)],walnut)
p=empty("bell_dome",r)
lathe("Spun brass bell",p,[(0,.025),(.015,.024),(.030,.011),(.040,-.008),
      (.043,-.020),(.042,-.025),(.037,-.025),(.035,-.009),(.027,.007),(0,.020)],brass)
p=empty("bell_button",r,(0,.031,0))
rod("Bell push stem",p,(0,-.020,0),(0,.0,0),.003,brass)
ball("Polished bell button",p,(.027,.015,.027),(0,0,0),brass)
r=roots["hourglass"]
for y in (-.074,.074):
    box("Hourglass crossbar",r,(.10,.012,.065),(0,y,0),walnut,.004)
for x in (-.042,.042): rod("Hourglass support",r,(x,-.070,0),(x,.070,0),.005,brass)
p=empty("hourglass_rotor",r)
for x in (-.035,.035): rod("Flip axle",r,(x,0,0),(x*1.3,0,0),.004,brass)
clear=atelier.pbr("Clear hourglass shell","glaze","D2DDD2",.12)
bs=clear.node_tree.nodes["Principled BSDF"];bs.inputs["Alpha"].default_value=.18
if hasattr(clear,"surface_render_method"): clear.surface_render_method="DITHERED"
lathe("Glass hourglass vessel",p,[(0,-.061),(.022,-.061),(.025,-.052),(.021,-.037),
      (.006,-.009),(.003,0),(.006,.009),(.021,.037),(.025,.052),(.022,.061),(0,.061)],clear)
for name,y,profile in [
    ("hourglass_sand_top",.010,[(0,0),(.018,.030),(.021,.040),(0,.040)]),
    ("hourglass_sand_bottom",-.051,[(0,0),(.021,0),(.018,.010),(0,.043)])]:
    q=empty(name,p,(0,y,0));lathe("Fine sand",q,profile,golden)
q=empty("hourglass_stream",p)
rod("Falling sand",q,(0,-.045,0),(0,.045,0),.0012,golden)
r=roots["windmill"]
box("Windmill desk plinth",r,(.115,.014,.095),(0,-.088,0),walnut)
rod("Windmill upright",r,(0,-.082,-.010),(0,.032,-.010),.007,oak)
box("Gear housing",r,(.047,.040,.042),(0,.026,-.010),olive,.006)
p=empty("windmill_rotor",r,(0,.026,.016))
rod("Rotor hub",p,(0,0,-.006),(0,0,.010),.012,brass,24)
for n in range(4):
    q=empty("Windmill blade "+str(n),p)
    q.rotation_euler.y=n*math.pi/2
    box("Lacquer blade",q,(.022,.053,.005),(.010,.043,.005),(slate,terra,olive,cream)[n])
    rod("Blade spine",q,(0,.010,0),(0,.067,0),.0018,brass)
p=empty("windmill_crank",r,(.030,.028,-.010))
rod("Crank axle",p,(-.010,0,0),(.005,0,0),.003,brass)
rod("Crank arm",p,(.005,0,0),(.005,-.027,0),.003,brass)
rod("Turned handle",p,(.005,-.027,0),(.025,-.027,0),.005,walnut)

# Fit static objects to their exact declared size and centred pivot.
# Animated mechanisms retain authored transforms; their specified envelope is checked separately.
bpy.context.view_layer.update()
manifest={}
for name,root in roots.items():
    meshes=[o for o in root.children_recursive if o.type=="MESH"]
    assert meshes, "Unbuilt asset: "+name
    points=[root.matrix_world.inverted()@o.matrix_world@Vector(c) for o in meshes for c in o.bound_box]
    lo=Vector(tuple(min(p[k] for p in points) for k in range(3)))
    hi=Vector(tuple(max(p[k] for p in points) for k in range(3)))
    if name not in ("slider","togglebox","abacus","bell","hourglass","windmill"):
        size=specs[name][1]
        factor=Vector((size[0]/(hi.x-lo.x),size[2]/(hi.y-lo.y),size[1]/(hi.z-lo.z)))
        offset=(lo+hi)/2
        for o in root.children:
            o.location=(o.location-offset)*factor
            o.scale*=factor
    bpy.context.view_layer.update()
atelier.apply()
for parent in list(roots.values())+[o for o in bpy.data.objects if o.type=="EMPTY" and o not in roots.values()]:
    meshes=[o for o in parent.children if o.type=="MESH"]
    if not meshes: continue
    bpy.ops.object.select_all(action="DESELECT")
    for o in meshes: o.select_set(True)
    bpy.context.view_layer.objects.active=meshes[0]
    if len(meshes)>1: bpy.ops.object.join()
    o=bpy.context.object;o.name=parent.name+"_mesh"
    bpy.ops.object.transform_apply(location=False,rotation=True,scale=True)
    mod=o.modifiers.new("Native triangles","TRIANGULATE");bpy.ops.object.modifier_apply(modifier=mod.name)
bpy.context.view_layer.update()
for name,root in roots.items():
    points=[root.matrix_world.inverted()@o.matrix_world@Vector(c) for o in root.children_recursive
            if o.type=="MESH" for c in o.bound_box]
    points=[(p.x,p.z,-p.y) for p in points]
    manifest[name]={"label":specs[name][0],"declaredSize":specs[name][1],
        "min":[min(p[k] for p in points) for k in range(3)],
        "max":[max(p[k] for p in points) for k in range(3)],
        "pivots":[o.name for o in root.children_recursive if o.type=="EMPTY"],
        "front":"+Z","unit":"metre"}
(OUT/"manifest.json").write_text(json.dumps(manifest,indent=2,ensure_ascii=False))
bpy.ops.object.select_all(action="SELECT")
bpy.ops.export_scene.gltf(filepath=str(ASSETS/"tidy-home-expansion.glb"),export_format="GLB",
    export_yup=True,export_apply=True,export_tangents=True,export_animations=False)
import subprocess
subprocess.run(["/usr/bin/python3",str(HERE/"repair_degenerate_tangents.py"),
                str(ASSETS/"tidy-home-expansion.glb")],check=True)
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/"home-expansion.blend"))
print("HOME_EXPANSION_COMPLETE",len(manifest),flush=True)
