"""Fourteen warm study objects; native metres, centred roots, semantic front +Z."""
import bpy,json,math,sys,subprocess
from pathlib import Path
from mathutils import Vector
HERE=Path(__file__).resolve().parent
ROOT=HERE.parent
PRIVATE=Path("/tmp/tidyspace-study-20261116")
source=(HERE/"build_home_expansion.py").read_text()
exec(compile(source[:source.index("oak = atelier.pbr")],str(HERE/"build_home_expansion.py"),"exec"))
atelier.TEXTURES=PRIVATE
spec=json.loads((ROOT.parent/"TidySpace-study-renovation-20261116/design-spec.json").read_text())
facts=next(n["props"] for n in spec["nodes"] if n["id"]=="scene")
sizes={a["id"]:a["size"] for a in facts["equipment"]}
oak=atelier.pbr("Study honey oak","wood","A98258",.53)
walnut=atelier.pbr("Study dark walnut","wood","6D513B",.57)
linen=atelier.pbr("Study oatmeal linen","cloth","D6C9AB",.92)
sage=atelier.pbr("Study sage velvet","cloth","829071",.86)
cream=atelier.pbr("Study warm paper","plain","EDE3CC",.89)
brass=atelier.pbr("Study aged brass","metal","B29050",.36,.72)
green=atelier.pbr("Study green opaline","glaze","3F694C",.22)
leaf=atelier.pbr("Study fern green","plain","4D7044",.68)
clay=atelier.pbr("Study ceramic ivory","glaze","E3D3AD",.25)
ink=atelier.pbr("Study printed ink","plain","5D6458",.85)
plaster=atelier.pbr("Study mineral plaster","plain","DDD5C3",.91)
books=[atelier.pbr("Study book "+str(i),"cloth",c,.81) for i,c in
       enumerate(("687858","A46D4E","C1A579","667E83","9E8580","544F47"))]
glow=atelier.pbr("Study warm diffuser","plain","F4D9A5",.48)
bs=glow.node_tree.nodes["Principled BSDF"]
bs.inputs["Emission Color"].default_value=(*atelier.linear(atelier.color("F4D9A5")),1)
bs.inputs["Emission Strength"].default_value=.65
roots={k:empty(k) for k in sizes}
def poly(name,r,pts,mat):
    mesh=bpy.data.meshes.new(name);mesh.from_pydata([xyz(p) for p in pts],[],[tuple(range(len(pts)))])
    mesh.update();o=bpy.data.objects.new(name,mesh);bpy.context.collection.objects.link(o)
    return finish(o,name,r,mat)
def seam(r,w,d,y,mat=cream):
    line("Tailored piping",r,[(-w/2,y,-d/2),(w/2,y,-d/2),(w/2,y,d/2),
         (-w/2,y,d/2),(-w/2,y,-d/2)],.0015,mat)
def book(r,x,y,z,w,h,d,index):
    mat=books[index%len(books)]
    box("Linen bound book",r,(w,h,d),(x,y+h/2,z),mat,.001)
    box("Ivory page block",r,(w-.003,h-.004,d-.003),(x+.001,y+h/2,z+.0015),cream,.0005)
    box("Cloth spine",r,(w,h,.003),(x,y+h/2,z+d/2),mat,.001)
    for yy in (.16,.22,.78,.84):
        box("Gilt spine band",r,(w-.003,.001,.0006),(x,y+h*yy,z+d/2+.002),brass,.0001)

r=roots["studydesk"]
box("Chamfered oak writing top",r,(.60,.022,.29),(0,.164,0),oak,.004)
box("Undertop apron",r,(.54,.045,.238),(0,.130,-.008),oak,.002)
for x in (-.257,.257):
    for z in (-.111,.111):
        rod("Tapered turned desk leg",r,(x,-.169,z),(x*.95,.142,z*.93),.010,oak)
        rod("Brass foot cap",r,(x,-.175,z),(x,-.155,z),.0105,brass)
for x in (-.138,.138):
    box("Framed drawer front",r,(.262,.036,.009),(x,.13,.117),oak,.002)
    box("Drawer inset",r,(.243,.026,.002),(x,.13,.123),walnut,.001)
    handle(r,x,.132,.132,.046,material=brass)
box("Back leg stretcher",r,(.50,.012,.014),(0,-.10,-.111),oak)

r=roots["studychair"]
box("Chair solid wood seat rim",r,(.218,.022,.217),(0,-.03,.006),oak,.010)
ball("Sage seat cushion",r,(.202,.035,.195),(0,-.013,.008),sage)
seam(r,.195,.186,-.005)
for x in (-.086,.086):
    for z in (-.087,.087):
        rod("Splayed wooden chair leg",r,(x*1.13,-.1675,z*1.13),(x,-.039,z),.009,oak)
for x in (-.10,.10):
    rod("Bentwood back stile",r,(x,-.035,-.096),(x,.145,-.096),.009,oak)
line("Rounded chair crown",r,[(-.10,.145,-.096),(-.075,.159,-.096),(0,.1675,-.096),(.075,.159,-.096),(.10,.145,-.096)],.008,oak)
box("Rattan back dark backing",r,(.178,.13,.003),(0,.08,-.101),walnut,.001)
for i in range(18):
    x=-.083+i*.0098
    rod("Vertical cane weave",r,(x,.020,-.094),(x,.142,-.094),.0015,linen,8)
for j in range(13):
    y=.023+j*.0095
    rod("Horizontal cane weave",r,(-.088,y,-.092),(.088,y,-.092),.0013,oak,8)
rod("Chair lower back rail",r,(-.10,.011,-.096),(.10,.011,-.096),.007,oak)

r=roots["studybookcase"]
box("Bookcase oak plinth",r,(.49,.032,.16),(0,-.344,0),oak)
box("Bookcase inset rear",r,(.46,.46,.008),(0,-.09,-.076),walnut,.001)
for x in (-.232,.232):box("Bookcase upright",r,(.022,.48,.16),(x,-.088,0),oak)
# A true curved arch, leaving a clear central shelf for the separate bookends.
arc=[(.232*math.cos(a),.128+.220*math.sin(a),.066) for a in [n*math.pi/40 for n in range(41)]]
line("Arched oak crown moulding",r,arc,.012,oak)
arcback=[(x,y,-.066) for x,y,z in arc]
line("Arched rear rim",r,arcback,.010,oak)
for a,b in zip(arc,arc[1:]):poly("Arch curved soffit",r,[(a[0],a[1],-.066),a,b,(b[0],b[1],-.066)],oak)
poly("Arch rear panel",r,[(-.223,.128,-.077),(.223,.128,-.077)]+[(x,y,-.077) for x,y,z in arc],walnut)
for y in (-.30,-.175,-.045,.125):
    box("Bookcase shelf",r,(.444,.020,.148),(0,y,0),oak,.002)
    box("Shelf edge bead",r,(.448,.006,.006),(0,y+.004,.074),brass,.001)
for row,y in enumerate((-.29,-.165,.135)):
    for i in range(9 if row<2 else 5):
        book(r,-.194+i*.039,y,-.005,.026,.090+(i%3)*.007,.105,i+row)
for x in (-.19,.19):
    box("Shelf carved corner",r,(.021,.037,.02),(x,-.093,.052),oak)

r=roots["studyarmchair"]
for x in (-.107,.107):
    for z in (-.106,.106):rod("Reading chair oak foot",r,(x,-.17,z),(x,-.102,z),.012,oak)
box("Rounded upholstered base",r,(.278,.070,.27),(0,-.07,.005),linen,.023)
ball("Deep reading cushion",r,(.235,.060,.226),(0,-.014,.020),linen)
box("Winged reading back",r,(.268,.245,.070),(0,.045,-.113),linen,.029)
ball("Sage lumbar cushion",r,(.170,.104,.043),(0,.056,-.06),sage)
for x in (-.128,.128):
    box("Soft rolled chair arm",r,(.044,.108,.242),(x,.012,.015),linen,.02)
seam(r,.222,.205,.004)
for x in (-.105,.105):line("Back stitched piping",r,[(x,-.025,-.071),(x,.139,-.071)],.0013,cream)

r=roots["studystool"]
for x in (-.071,.071):
    for z in (-.062,.062):rod("Footstool turned foot",r,(x,-.0725,z),(x,-.015,z),.010,oak)
box("Wool footstool pad",r,(.2,.077,.18),(0,.034,0),linen,.025)
seam(r,.18,.16,.055)
for x in (-.048,.048):
    ball("Tufted cloth button",r,(.007,.002,.007),(x,.072,0),linen)

r=roots["studyside"]
for y,radius in ((.099,.080),(-.041,.066)):
    lathe("Round oak tray",r,[(0,y-.006),(radius-.005,y-.006),(radius,y),(radius-.002,y+.006),(0,y+.006)],oak)
for a in (0,math.tau/3,math.tau*2/3):
    x,z=.053*math.cos(a),.053*math.sin(a)
    rod("Side table brass leg",r,(x,-.105,z),(x,.095,z),.004,brass)

r=roots["studybanker"]
lathe("Banker lamp stepped base",r,[(0,-.09),(.037,-.09),(.042,-.085),(.038,-.078),(0,-.075)],brass)
rod("Banker upright",r,(0,-.079,-.011),(0,.057,-.011),.0045,brass)
for x in (-.049,.049):rod("Shade pivot arm",r,(0,.042,-.011),(x,.052,-.011),.003,brass)
# Opaline half-cylinder with visible cream underside, end caps and pull chain.
for n in range(24):
    a,b=n*math.pi/24,(n+1)*math.pi/24
    poly("Green curved opaline shade",r,[(-.0625,.05+.04*math.sin(a),.04*math.cos(a)),
        (.0625,.05+.04*math.sin(a),.04*math.cos(a)),(.0625,.05+.04*math.sin(b),.04*math.cos(b)),
        (-.0625,.05+.04*math.sin(b),.04*math.cos(b))],green)
for x in (-.0625,.0625):
    poly("Shade rounded end",r,[(x,.05,.04),(x,.05,-.04)]+[(x,.05+.04*math.sin(a),.04*math.cos(a)) for a in [n*math.pi/24 for n in range(25)]],green)
box("Warm glass underside",r,(.115,.002,.066),(0,.051,0),glow,.001)
line("Beaded pull chain",r,[(.040,.048,.017),(.040,-.017,.017)],.0008,brass)
ball("Chain pull",r,(.005,.008,.005),(.040,-.019,.017),brass)

r=roots["studybook"]
for x,sign in ((-.038,-1),(.038,1)):
    box("Open book leather cover",r,(.078,.003,.102),(x,-.007,0),books[1],.001)
    poly("Curved open pages",r,[(0,.006,-.048),(sign*.075,.002,-.048),(sign*.075,.002,.048),(0,.006,.048)],cream)
    for n in range(11):
        z=-.035+n*.0064
        box("Readable page line",r,(.047 if n%3 else .036,.0003,.0006),(x,.004,z),ink,.0001)
    for y in (-.004,-.002,0):
        rod("Layered paper edge",r,(sign*.075,y,-.047),(sign*.075,y,.047),.0003,cream,6)
rod("Fountain pen",r,(.080,-.002,-.04),(.080,-.002,.04),.003,walnut)
rod("Pen gold nib",r,(.080,-.002,-.049),(.080,-.002,-.04),.0018,brass)

r=roots["studyteaset"]
lathe("Tea saucer",r,[(0,-.0375),(.035,-.0375),(.043,-.033),(.04,-.030),(0,-.031)],clay,p=(-.006,0,0))
lathe("Hollow ceramic cup",r,[(.018,-.030),(.024,-.026),(.027,.030),(.024,.033),(.022,.030),(.019,-.022),(0,-.022)],clay,p=(-.007,0,0))
lathe("Tea surface",r,[(0,.021),(.022,.021)],books[1],p=(-.007,0,0))
ring("Ceramic loop handle",r,.012,.003,(.026,.006,0),clay,front=True)
for x in (-.022,.008):
    ball("Almond biscuit",r,(.020,.009,.013),(x,-.025,.033),books[2])
rod("Small brass teaspoon",r,(-.037,-.027,-.027),(.024,-.027,-.027),.0015,brass)

r=roots["studyfern"]
lathe("Woven basket planter",r,[(0,-.135),(.039,-.135),(.048,-.045),(.042,-.036),(.039,-.045)],oak)
for y in [-.125+n*.006 for n in range(15)]:ring("Basket woven coil",r,.040+(y+.125)*.07,.0013,(0,y,0),linen)
lathe("Dark planter soil",r,[(0,-.04),(.040,-.04)],walnut)
for n in range(11):
    a=n*2.39996;reach=.060+(n%3)*.006;top=.055+(n%4)*.021
    points=[]
    for j in range(10):
        t=j/9;points.append((reach*t*math.cos(a),-.04+(top+.04)*math.sin(t*1.25),reach*t*math.sin(a)))
    line("Curved fern stem",r,points,.0011,leaf)
    for j,p in enumerate(points[2:9]):
        length=.024*(1-j/10)
        for sign in (-1,1):
            end=(p[0]+sign*length*math.cos(a+math.pi/2),p[1]+.006,p[2]+sign*length*math.sin(a+math.pi/2))
            mid=tuple((u+v)/2 for u,v in zip(p,end))
            poly("Lanceolate fern leaflet",r,[p,(mid[0]+.004*math.cos(a),mid[1]+.003,mid[2]+.004*math.sin(a)),end,
                 (mid[0]-.004*math.cos(a),mid[1],mid[2]-.004*math.sin(a))],leaf)

r=roots["studyrug"]
box("Soft woven wool rug",r,(.64,.004,.66),(0,0,0),linen,.001)
for x in (-.301,.301):
    box("Sage rug woven border",r,(.018,.0005,.634),(x,.0022,0),sage,.0001)
for z in (-.311,.311):box("Sage rug end border",r,(.60,.0005,.014),(0,.0022,z),sage,.0001)
for j in range(50):
    z=-.32+j*.013
    for sign in (-1,1):
        rod("Hand knotted fringe",r,(sign*.32,-.0005,z),(sign*.345,-.0005,z+.002),.001,linen,6)

r=roots["studyglobe"]
lathe("Globe turned wooden base",r,[(0,-.075),(.033,-.075),(.035,-.071),(.029,-.064),(0,-.063)],walnut)
rod("Globe brass pedestal",r,(0,-.065,0),(0,-.027,0),.004,brass)
ball("Sage globe ocean",r,(.078,.078,.078),(0,.017,0),books[3])
ring("Meridian brass ring",r,.045,.002,(0,.017,0),brass,front=True)
for n in range(-2,3):
    yy=n*.012;rr=math.sqrt(.0392**2-yy**2)
    ring("Fine latitude engraving",r,rr,.00035,(0,.017+yy,0),cream)
for a in (0,math.pi/3,2*math.pi/3):
    pts=[(.0394*math.sin(t)*math.cos(a),.017+.0394*math.cos(t),.0394*math.sin(t)*math.sin(a)) for t in [j*math.tau/60 for j in range(61)]]
    line("Fine longitude engraving",r,pts,.0003,cream)
for lon,lat in ((.4,.3),(1.8,.6),(2.4,-.4),(3.8,.2),(5.3,-.45)):
    pts=[]
    for j in range(12):
        t=j*math.tau/12;l=lon+.29*math.cos(t)*(1+.25*math.sin(t*3));v=lat+.37*math.sin(t)
        pts.append((.0397*math.cos(v)*math.cos(l),.017+.0397*math.sin(v),.0397*math.cos(v)*math.sin(l)))
    poly("Stylised atlas landmass",r,pts,books[2])

r=roots["studyfloorlamp"]
lathe("Weighted floorlamp base",r,[(0,-.255),(.052,-.255),(.057,-.249),(.05,-.239),(0,-.239)],brass)
rod("Slender floorlamp stem",r,(0,-.24,0),(0,.20,0),.004,brass)
lathe("Warm shade underside",r,[(0,.12),(.056,.12)],glow)
for j in range(64):
    a,b=j*math.tau/64,(j+1)*math.tau/64
    ra=.06 if j%2 else .057;rb=.06 if (j+1)%2 else .057
    poly("Pleated linen shade",r,[(ra*math.cos(a),.12,ra*math.sin(a)),(rb*math.cos(b),.12,rb*math.sin(b)),
       (rb*.66*math.cos(b),.255,rb*.66*math.sin(b)),(ra*.66*math.cos(a),.255,ra*.66*math.sin(a))],linen)
for y,rr in ((.12,.059),(.254,.039)):ring("Shade bound hem",r,rr,.0013,(0,y,0),cream)

r=roots["studybookends"]
for i in range(5):book(r,-.050+i*.025,-.064,0,.021,.105+(i%3)*.013,.080,i)
for sign in (-1,1):
    box("Bookend brass foot",r,(.031,.007,.095),(sign*.069,-.064,0),brass,.002)
    box("Bookend carved upright",r,(.009,.101,.085),(sign*.080,-.011,0),oak,.002)
    for z in (-.025,0,.025):rod("Carved bookend flute",r,(sign*.086,-.052,z),(sign*.086,.031,z),.002,brass)

# Reuse the proven GLB finalization: UVs, AO, tangent repair, precise native bounds.
source=(HERE/"build_gaming.py").read_text()
tail=source[source.index("# Match the catalogue pivots exactly."):source.index('bpy.ops.object.select_all(action="SELECT");bpy.ops.object.delete(use_global=False)')]
tail=tail.replace("(blue,cloth)","(linen,sage)").replace("tidy-gaming.glb","tidy-study.glb").replace("gaming-manifest","study-manifest").replace("gaming-models","study-models")
exec(compile(tail,str(HERE/"build_gaming.py"),"exec"))

bpy.ops.object.select_all(action="SELECT");bpy.ops.object.delete(use_global=False)
house=empty("study_house")
floor=empty("study_continuous_floor",house)
left,right,back,front=-1.53265,-.10265,.899,2.259
cx,cz=(left+right)/2,(back+front)/2
box("Study oak foundation",floor,(right-left+.028,.060,front-back+.028),(cx,-.358,cz),oak,.005)
box("Study parquet underlay",floor,(right-left,.016,front-back),(cx,-.330,cz),walnut,.001)
atelier.parquet(floor,(left,right,back,front))
box("Flush existing floor overlap",floor,(right-left,.0015,.058),(cx,-.31925,back+.029),oak,.0003)
# The study is open to the gaming area and cutaway edge.
for o in bpy.data.objects:
    if o.type=="MESH":atelier.project(o,tile=.48)
atelier.bake_occlusion();batch([floor])
export(ROOT/"app/src/main/assets/models/tidy-study-room.glb",[house])
bpy.ops.wm.save_as_mainfile(filepath=str(PRIVATE/"study-room.blend"))
print("STUDY_ASSETS_COMPLETE",len(manifest),flush=True)
