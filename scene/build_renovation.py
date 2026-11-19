"""Original cutaway attic, articulated cleaning tools and mess, PICO metre/Y-up GLB."""
import bpy
import math
import json
import random
import sys
from pathlib import Path
from mathutils import Vector

HERE=Path(__file__).resolve().parent
sys.path.insert(0,str(HERE))
import art_direction as atelier
OUT=HERE/"renovation"
OUT.mkdir(exist_ok=True)
ASSETS=HERE.parent/"app/src/main/assets/models"
random.seed(381)
bpy.ops.object.select_all(action="SELECT")
bpy.ops.object.delete(use_global=False)
def xyz(p): return (p[0],-p[2],p[1])
def material(name,hex,rough=.7,metal=0,texture=None):
    m=bpy.data.materials.new(name);m.use_nodes=True
    bs=m.node_tree.nodes.get("Principled BSDF")
    rgb=[int(hex[i:i+2],16)/255 for i in (0,2,4)]
    bs.inputs["Base Color"].default_value=tuple(c/12.92 if c<=.04045 else ((c+.055)/1.055)**2.4 for c in rgb)+(1,)
    bs.inputs["Roughness"].default_value=rough;bs.inputs["Metallic"].default_value=metal
    if texture:
        for suffix,slot in (("base","Base Color"),("rough","Roughness"),("normal","Normal")):
            image=bpy.data.images.load(str(HERE/"furniture"/f"{texture}_{suffix}.png"),check_existing=True)
            if suffix!="base": image.colorspace_settings.name="Non-Color"
            image.pack();node=m.node_tree.nodes.new("ShaderNodeTexImage");node.image=image
            if suffix=="normal":
                normal=m.node_tree.nodes.new("ShaderNodeNormalMap");normal.inputs["Strength"].default_value=.4
                m.node_tree.links.new(node.outputs["Color"],normal.inputs["Color"])
                m.node_tree.links.new(normal.outputs["Normal"],bs.inputs[slot])
            else: m.node_tree.links.new(node.outputs["Color"],bs.inputs[slot])
    return m
oak=material("Oiled parquet","BBA17B",.6,texture="oak")
walnut=material("Deep walnut end grain","775839",.65,texture="oak")
cream=material("Warm ivory plaster","EEE1C8")
sage=material("Faded sage wallpaper","9BA68A")
trim=material("Chalk painted joinery","F3E8D1",.5)
brick=[material(f"Hand fired brick {i}",c) for i,c in enumerate(["A5583E","BA6A49","C57A58","9F503B"])]
grout=material("Recessed old mortar","827064")
roof=[material(f"Terracotta roof {i}",c) for i,c in enumerate(["AF553E","C46B4B","A44F3C"])]
iron=material("Aged iron","333A36",.4,.7)
gold=material("Brass details","B39B62",.3,.65)
green=material("Enamel garden green","416C56",.35)
mint=material("Mint cleaning enamel","9BCAB3",.36)
rubber=material("Soft rubber grip","35463F",.9)
bristle=material("Golden natural bristles","BA9753",.88)
soil=material("Accumulated dirt","63513D",1)
paper=material("Crumpled paper","D3C7AE",.95)
cardboard=material("Corrugated carton","B28A57",.95,texture="oak")
red=material("Discarded red cups","A4493D",.5)
leaf=material("Ivy leaves","4F7546")
roots={}
def empty(name,parent=None,p=(0,0,0)):
    o=bpy.data.objects.new(name,None);bpy.context.collection.objects.link(o)
    o.parent=parent;o.location=xyz(p);roots[name]=o;return o
def finish(o,name,parent,m):
    o.name=name;o.parent=parent;o.data.materials.append(m)
    return o
def box(name,parent,size,p,m,bevel=.002):
    bpy.ops.mesh.primitive_cube_add(size=1,location=xyz(p));o=bpy.context.object
    o.dimensions=(size[0],size[2],size[1]);bpy.ops.object.transform_apply(location=False,rotation=False,scale=True)
    if bevel:
        mod=o.modifiers.new("Crafted edge","BEVEL");mod.width=min(bevel,min(size)*.3);mod.segments=2
        bpy.ops.object.modifier_apply(modifier=mod.name)
    return finish(o,name,parent,m)
def ball(name,parent,size,p,m,ico=False):
    if ico: bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=1,radius=1,location=xyz(p))
    else: bpy.ops.mesh.primitive_uv_sphere_add(segments=16,ring_count=8,radius=1,location=xyz(p))
    o=bpy.context.object;o.scale=(size[0]/2,size[2]/2,size[1]/2)
    bpy.ops.object.transform_apply(location=False,rotation=False,scale=True)
    for f in o.data.polygons: f.use_smooth=not ico
    return finish(o,name,parent,m)
def rod(name,parent,a,b,r,m):
    delta=Vector(xyz(b))-Vector(xyz(a))
    bpy.ops.mesh.primitive_cylinder_add(vertices=12,radius=r,depth=delta.length,location=(Vector(xyz(a))+Vector(xyz(b)))/2)
    o=bpy.context.object;o.rotation_euler=delta.to_track_quat("Z","Y").to_euler()
    return finish(o,name,parent,m)
def polygon(name,parent,points,depth,m):
    verts=[xyz((x,y,z)) for x,y,z in points]+[xyz((x,y,z-depth)) for x,y,z in points]
    n=len(points);faces=[tuple(range(n-1,-1,-1)),tuple(range(n,2*n))]
    faces += [(i,(i+1)%n,(i+1)%n+n,i+n) for i in range(n)]
    mesh=bpy.data.meshes.new(name);mesh.from_pydata(verts,[],faces);mesh.update()
    o=bpy.data.objects.new(name,mesh);bpy.context.collection.objects.link(o);return finish(o,name,parent,m)

house=empty("renovation_house")
floor=empty("renovation_base",house)
box("Cutaway foundation",floor,(2.25,.06,1.68),(0,-.358,-.18),walnut,.007)
# Genuine herringbone joinery uses the same floor elevation and outer bounds.
atelier.parquet(house,(-1.113,1.113,-.98,.638))
# True apertures match existing furniture-wall physics.
for center,size in [
    ((0,-.095,-.986),(2.25,.45,.068)),((0,.65,-.986),(2.25,.14,.068)),
    ((-.9825,.355,-.986),(.285,.45,.068)),((.4725,.355,-.986),(1.305,.45,.068))]:
    box("Back plaster",house,size,center,cream,bevel=0)
for center,size in [
    ((-1.091,-.12,-.161),(.068,.40,1.5825)),((-1.091,.63,-.161),(.068,.18,1.5825)),
    ((-1.091,.31,-.799),(.068,.46,.3075)),((-1.091,.31,.2925),(.068,.46,.675))]:
    box("Side wallpaper",house,size,center,sage,bevel=0)
for x in (-.855,-.17):
    box("Window jamb",house,(.041,.50,.115),(x,.355,-.966),trim)
for y in (.112,.598):
    box("Window cross rail",house,(.77,.032,.115),(-.5125,y,-.966),trim)
for x in (-.626,-.397):
    box("Window muntin",house,(.017,.45,.03),(x,.355,-.937),trim)
box("Window sash",house,(.68,.018,.038),(-.5125,.350,-.935),trim)
box("Deep window sill",house,(.80,.031,.19),(-.5125,.088,-.89),trim,.004)
for z in (-.665,-.023):
    box("Side jamb",house,(.118,.50,.040),(-1.066,.31,z),trim)
for y in (.066,.554):
    box("Side sash rail",house,(.118,.033,.70),(-1.066,y,-.344),trim)
box("Side muntin",house,(.032,.46,.016),(-1.047,.31,-.344),trim)
box("Side sash",house,(.032,.016,.63),(-1.047,.31,-.344),trim)
box("Side sill",house,(.18,.027,.75),(-1.02,.042,-.344),trim)
for z in (-.985,.605):
    box("Corner upright",house,(.04,1.04,.038),(-1.055,.20,z),walnut)
box("Back walnut skirting",house,(2.14,.072,.026),(0,-.283,-.938),walnut)
box("Side walnut skirting",house,(.026,.072,1.54),(-1.035,-.283,-.16),walnut)
box("Back skirting molded bead",house,(2.14,.009,.032),(0,-.250,-.938),walnut,.002)
box("Side skirting molded bead",house,(.032,.009,1.54),(-1.035,-.250,-.16),walnut,.002)
# Quiet mineral walls reproduce the reference; architecture carries the detail.
for x in (-.855,-.17):
    box("Recessed window frame bead",house,(.010,.476,.009),(x,.355,-.904),trim,.001)
for z in (-.665,-.023):
    box("Recessed side frame bead",house,(.009,.477,.009),(-1.002,.31,z),trim,.001)
# Gabled attic and rear-only roof leave hands and gaze unobstructed.
polygon("Attic gable",house,[(-1.125,.72,-.951),(1.125,.72,-.951),(.08,1.065,-.951)],.067,cream)
for x1,x2,y1,y2 in [(-1.17,.08,.72,1.085),(.08,1.17,1.085,.72)]:
    rod("Gable timber rake",house,(x1,y1,-.927),(x2,y2,-.927),.018,walnut)
    for row in range(4):
        z=-1.08+row*.075
        for col in range(12):
            t=(col+.5)/12;x=x1+(x2-x1)*t;y=y1+(y2-y1)*t+.004
            # Barrel relief is actual clay geometry with a thin, dark lower lip.
            width=abs(x2-x1)/12+.004
            verts=[];faces=[]
            for end in (-.5,.5):
                for k in range(9):
                    t=k/8
                    verts.append((end*width,(t-.5)*.096,.009*math.sin(t*math.pi)))
            for k in range(8):faces.append((k,k+1,k+10,k+9))
            mesh=bpy.data.meshes.new("Barrel roof tile")
            mesh.from_pydata(verts,[],faces);mesh.update()
            tile=bpy.data.objects.new("Individual barrel roof tile",mesh)
            bpy.context.collection.objects.link(tile)
            tile.location=xyz((x,y,z));finish(tile,tile.name,house,roof[(col+row)%3])
            solid=tile.modifiers.new("Terracotta tile thickness","SOLIDIFY");solid.thickness=.006
            bpy.context.view_layer.objects.active=tile;bpy.ops.object.modifier_apply(modifier=solid.name)
            tile.rotation_euler.y=-math.atan2(y2-y1,x2-x1)
box("Left roof cutaway eave",house,(.07,.046,1.76),(-1.14,.728,-.18),roof[0],.005)
# External brick corners show wall thickness; recessed mortar remains behind bricks.
for row in range(16):
    y=-.28+row*.062
    for col in range(4):
        x=-1.09+col*.068
        box("Exterior brick",house,(.062,.053,.019),(x,y,-1.03),brick[(row+col)%4],.002)
# Fireplace: hollow black opening, molded mantel, turned columns, grate, logs and chimney.
fire=empty("renovation_fireplace",house,(-1.018,-.32,.29))
box("Stone hearth",fire,(.27,.018,.39),(.04,.009,0),trim,.004)
box("Dark firebox back",fire,(.016,.24,.23),(.014,.144,0),iron)
for z in (-.143,.143):
    box("Mantel pier",fire,(.20,.30,.050),(.025,.168,z),trim,.004)
    for dz in (-.012,.012): rod("Fluted stone column",fire,(.127,.04,z+dz),(.127,.285,z+dz),.008,cream)
box("Mantel lintel",fire,(.215,.073,.35),(.035,.310,0),trim,.004)
box("Mantel crown",fire,(.25,.029,.39),(.04,.361,0),trim,.005)
for z in (-.076,0,.076):
    rod("Fireplace log",fire,(.039,.045,z-.02),(.100,.054,z+.02),.015,walnut)
for z in (-.10,-.05,0,.05,.10):
    rod("Iron fire grate",fire,(.15,.025,z),(.15,.102,z),.003,iron)
# Delicate wrought iron scroll screen, framed around the live fire.
for z in (-.124,0,.124):
    rod("Fire screen vertical",fire,(.151,.036,z),(.151,.254,z),.0025,iron)
for y in (.036,.254):
    rod("Fire screen border",fire,(.151,y,-.124),(.151,y,.124),.0025,iron)
for z in (-.063,.063):
    for y in (.097,.193):
        points=[]
        for n in range(27):
            a=n/26*math.tau*1.35;radius=.037*(1-n/32)
            points.append((.152,y+radius*math.sin(a),z+radius*math.cos(a)))
        for a,b in zip(points,points[1:]):rod("Forged iron scroll",fire,a,b,.0018,iron)
for z in (-.105,.105):
    ball("Brass fire screen rivet",fire,(.005,.005,.005),(.155,.241,z),gold)
box("Chimney",house,(.19,.52,.24),(-1.027,.79,.29),grout)
box("Plastered chimney breast",house,(.19,.49,.24),(-1.027,.30,.29),cream)
for row in range(8):
    for col in range(3):
        box("Chimney brick",house,(.061,.056,.016),(-1.095+col*.068,.56+row*.061,.417),brick[(row+col)%4])
box("Chimney cap",house,(.225,.026,.275),(-1.027,1.066,.29),trim)
# The balcony runs the entire side wall: the same -1.02..0.66 foundation span.
# Keep its width, floor height and joinery thickness; lengthen only the deck.
balcony_back,balcony_front=-1.02,.66
balcony_center=(balcony_back+balcony_front)/2
balcony=empty("renovation_balcony",house)
box("Balcony foundation",balcony,(.37,.065,balcony_front-balcony_back),
    (1.307,-.357,balcony_center),walnut,.005)
for col in range(7):
    for row in range(6):
        box("Balcony deck",balcony,(.049,.008,.272),
            (1.15+col*.052,-.324,-1.005+(row+.5)*.275),oak,.001)
for z in (balcony_back+.01,balcony_front-.01):
    rod("Balcony railing",balcony,(1.13,-.08,z),(1.49,-.08,z),.012,trim)
    for x in (1.16,1.27,1.38,1.48): rod("Baluster",balcony,(x,-.32,z),(x,-.08,z),.006,trim)
rod("Outer balcony handrail",balcony,(1.49,-.08,balcony_back+.01),
    (1.49,-.08,balcony_front-.01),.012,trim)
for n in range(16):
    z=balcony_back+.04+n*(balcony_front-balcony_back-.08)/15
    rod("Baluster",balcony,(1.49,-.32,z),(1.49,-.08,z),.006,trim)
for side in (0,1):
    for n in range(22):
        y=.68-n*.036;z=.61+math.sin(n*.8)*.025
        ball("Exterior climbing ivy",house,(.030,.045,.019),(-1.16+side*.035,y,z),leaf)

bin=empty("clean_bin",p=(.88,-.12,.42))
for x in (-.146,.146): box("Open bin side",bin,(.012,.26,.28),(x,-.11,0),green)
for z in (-.139,.139): box("Open bin face",bin,(.29,.26,.012),(0,-.11,z),green)
box("Bin bottom",bin,(.29,.014,.28),(0,-.242,0),green)
for x in (-.105,.105): ball("Bin rubber wheels",bin,(.048,.048,.032),(x,-.23,-.135),rubber)
box("Bin open hinged lid",bin,(.31,.016,.28),(0,.135,-.15),green,.004).rotation_euler.x=math.radians(70)

vac=empty("clean_Vacuum",p=(-.65,-.30,.48))
box("Vacuum working nozzle",vac,(.16,.033,.083),(0,.002,0),mint,.008)
box("Rubber suction mouth",vac,(.125,.005,.060),(0,-.014,0),rubber)
rod("Vacuum aluminium tube",vac,(0,.016,-.014),(0,.28,-.047),.011,iron)
ball("Motor capsule",vac,(.07,.12,.07),(0,.245,-.045),mint)
rod("Vacuum soft hand grip",vac,(0,.29,-.047),(0,.36,-.030),.016,rubber)
for x in (-.067,.067): ball("Nozzle wheel",vac,(.025,.027,.025),(x,0,0),rubber)
broom=empty("clean_Broom",p=(-.39,-.30,.48))
box("Broom head",broom,(.17,.027,.045),(0,.025,0),walnut,.003)
broom_bristles=empty("broom_bristles",broom)
for n in range(25): rod("Individual bristle bundle",broom_bristles,(-.077+n*.0064,.014,0),(-.083+n*.0069,-.012,.003),.0028,bristle)
rod("Broom handle",broom,(0,.032,0),(0,.40,-.025),.008,oak)
rod("Broom hand grip",broom,(0,.35,-.023),(0,.41,-.028),.010,mint)
brush=empty("clean_Brush",p=(-.13,-.17,.48))
stand=empty("clean_stand")
box("Brush resting stand",stand,(.13,.125,.12),(-.13,-.2575,.48),mint,.005)
box("Wall brush curved block",brush,(.105,.055,.026),(0,0,.014),mint,.006)
brush_bristles=empty("brush_bristles",brush)
for row in range(4):
    for col in range(9): rod("Wall brush bristles",brush_bristles,(-.042+col*.0105,-.019+row*.013,0),(-.042+col*.0105,-.019+row*.013,-.019),.0024,bristle)
rod("Wall brush handle",brush,(0,.018,.025),(0,.13,.05),.012,oak)

# One connected organic outline per broad area. Invisible strip boundaries let
# Kotlin reveal clean wood/plaster in a sweep, without shrinking raised blobs.
layout=[
    ("dust-0","floor",(-.49,-.3194,-.59),(.72,.45),13),
    ("dust-1","floor",(.43,-.3194,-.12),(.78,.46),29),
    ("debris-0","debris",(-.24,-.305,-.08),(.53,.36),47),
    ("debris-1","debris",(.57,-.305,-.66),(.64,.31),73),
    ("wall-0","wall",(-.48,-.135,-.9513),(.72,.30),91),
    ("wall-1","wall",(.55,.25,-.9513),(.74,.53),109),
]
def stain_material(name,kind,seed):
    rng=random.Random(seed);phase=rng.uniform(0,math.tau);pixels=[];res=512
    fields=[(n,[[rng.random() for _ in range(n+1)] for _ in range(n+1)]) for n in (7,19,47)]
    def field(index,u,v):
        n,values=fields[index];px=u*n;py=v*n;ix=int(px);iy=int(py)
        fx=px-ix;fy=py-iy;fx=fx*fx*(3-2*fx);fy=fy*fy*(3-2*fy)
        return (values[iy][ix]*(1-fx)+values[iy][ix+1]*fx)*(1-fy)+(
            values[iy+1][ix]*(1-fx)+values[iy+1][ix+1]*fx)*fy
    for row in range(res):
        y=(row+.5)/res*2-1
        for col in range(res):
            x=(col+.5)/res*2-1;a=math.atan2(y,x)
            edge=.82+.09*math.sin(3*a+phase)+.04*math.cos(5*a-phase)
            r=math.hypot(x,y)/edge
            feather=max(0,min(1,(1-r)*4))
            feather=feather*feather*(3-2*feather)
            u=(col+.5)/res;v=(row+.5)/res
            noise=.12+.50*field(0,u,v)+.22*field(1,u,v)+.12*field(2,u,v)
            alpha=feather*noise*(1.20 if kind=="wall" else 1.35)
            alpha=min(.92,alpha)
            tone=(.24,.20,.14) if kind=="wall" else (.28,.23,.16)
            pixels.extend((*tone,alpha))
    image=bpy.data.images.new(name+" soft grime RGBA",width=res,height=res,alpha=True)
    image.pixels.foreach_set(pixels)
    image.filepath_raw=str(OUT/(name+"-grime.png"));image.file_format="PNG";image.save();image.pack()
    m=material(name+" translucent surface dirt","FFFFFF",1)
    m.surface_render_method="BLENDED"
    m.use_backface_culling=False
    bs=m.node_tree.nodes.get("Principled BSDF")
    tex=m.node_tree.nodes.new("ShaderNodeTexImage");tex.image=image
    m.node_tree.links.new(tex.outputs["Color"],bs.inputs["Base Color"])
    m.node_tree.links.new(tex.outputs["Alpha"],bs.inputs["Alpha"])
    return m
def cell_material(stain,name,kind,row,col,bounds,size):
    """Overlapping feathered UV patches form a partition of optical density.

    Fully dirty patches retain the continuous original texture. Removing a
    patch leaves a soft brush-width edge rather than a rectangular cutout.
    """
    source=next(n.image for n in stain.node_tree.nodes if n.type=="TEX_IMAGE")
    raw=list(source.pixels);source_res=source.size[0]
    lo,hi,bottom,top=bounds
    w=size[0]/8;h=size[1]/3
    res=64;pixels=[]
    def smooth(v):
        t=max(0,min(1,v));return t*t*(3-2*t)
    for y in range(res):
        b=bottom+(top-bottom)*(y+.5)/res
        for x in range(res):
            a=lo+(hi-lo)*(x+.5)/res
            u=max(0,min(source_res-1,int((a/size[0]+.5)*source_res)))
            v=max(0,min(source_res-1,int((b/size[1]+.5)*source_res)))
            offset=(v*source_res+u)*4
            mask=(1 if col==0 else smooth((a-lo)/(.24*w)))*(
                1 if col==7 else smooth((hi-a)/(.24*w)))*(
                1 if row==0 else smooth((b-bottom)/(.24*h)))*(
                1 if row==2 else smooth((top-b)/(.24*h)))
            pixels.extend((*raw[offset:offset+3],1-(1-raw[offset+3])**mask))
    image=bpy.data.images.new(f"{name} cell {row*8+col} feathered",width=res,height=res,alpha=True)
    image.pixels.foreach_set(pixels);image.pack()
    m=stain.copy();m.name=f"{name} cell {row*8+col} translucent surface dirt"
    next(n for n in m.node_tree.nodes if n.type=="TEX_IMAGE").image=image
    return m
for name,kind,p,size,seed in layout:
    rng=random.Random(seed)
    root=empty(name,p=p)
    if kind=="debris":
        bands=[empty(f"{name}_wipe-{i}",root) for i in range(8)]
        # Two different, loosely clustered spills, with varied leaf/paper shapes.
        for i in range(28):
            a=rng.uniform(0,math.tau);r=math.sqrt(rng.random())*.90
            x=math.cos(a)*r*size[0]/2;z=math.sin(a)*r*size[1]/2
            band=bands[min(7,max(0,int((x/size[0]+.5)*8)))]
            piece=empty(f"{name}_piece-{i}",band,p=(x,0,z))
            if i%3:
                length=rng.uniform(.016,.041);width=rng.uniform(.008,.023)
                mesh=bpy.data.meshes.new("Curled leaf")
                verts=[xyz(v) for v in [(-length/2,-.008,0),(0,-.004,-width/2),
                    (length/2,-.007,0),(0,-.003,width/2),(0,.002,0)]]
                mesh.from_pydata(verts,[],[(0,1,4),(1,2,4),(2,3,4),(3,0,4)])
                mesh.update();o=bpy.data.objects.new("Curled leaf",mesh);bpy.context.collection.objects.link(o)
                finish(o,"Curled leaf",piece,leaf if seed==47 else paper)
            else:
                ball("Uneven chip",piece,(rng.uniform(.010,.024),.010,rng.uniform(.009,.020)),
                    (0,-.006,0),cardboard if seed==47 else soil,True)
        continue
    stain=stain_material(name,kind,seed)
    for row in range(3):
        for band in range(8):
            w=size[0]/8;h=size[1]/3
            lo=max(-size[0]/2,-size[0]/2+w*(band-.12))
            hi=min(size[0]/2,-size[0]/2+w*(band+1.12))
            bottom=max(-size[1]/2,-size[1]/2+h*(row-.12))
            top=min(size[1]/2,-size[1]/2+h*(row+1.12))
            patch=cell_material(stain,name,kind,row,band,(lo,hi,bottom,top),size)
            points=[(lo,bottom),(hi,bottom),(hi,top),(lo,top)]
            # One joined model per surface, with a distinct material per cell.
            # The public PBR opacity controls local removal without extra layers.
            depth=.00045+(row*8+band)*.00001
            verts=[xyz((x,y,depth)) if kind=="wall" else xyz((x,depth,y)) for x,y in points]
            face=(0,1,2,3) if kind=="wall" else (3,2,1,0)
            mesh=bpy.data.meshes.new("Soft surface stain");mesh.from_pydata(verts,[],[face]);mesh.update()
            uv=mesh.uv_layers.new(name="UVMap")
            for poly in mesh.polygons:
                for loop_index in poly.loop_indices:
                    x,y=points[mesh.loops[loop_index].vertex_index]
                    uv.data[loop_index].uv=((x-lo)/(hi-lo),(y-bottom)/(top-bottom))
            o=bpy.data.objects.new("Soft surface stain",mesh);bpy.context.collection.objects.link(o)
            finish(o,"Soft surface stain",root,patch)

for n,p in enumerate([(-.68,-.265,-.10),(.12,-.265,-.54),(.66,-.265,.18)]):
    trash=empty(f"trash-{n}",p=p)
    if n==0:
        for x in (-.053,.053): box("Crushed carton side",trash,(.008,.08,.095),(x,-.01,0),cardboard)
        for z in (-.046,.046): box("Crushed carton face",trash,(.105,.08,.008),(0,-.01,z),cardboard)
        box("Carton base",trash,(.11,.008,.10),(0,-.05,0),cardboard)
        flap=box("Folded carton flap",trash,(.06,.006,.10),(.025,.033,0),cardboard);flap.rotation_euler.y=.4
    elif n==1:
        bpy.ops.mesh.primitive_cone_add(vertices=24,radius1=.027,radius2=.042,depth=.10,location=xyz((0,-.005,0)))
        finish(bpy.context.object,"Paper cup",trash,red)
        bpy.ops.mesh.primitive_torus_add(major_radius=.039,minor_radius=.003,major_segments=24,minor_segments=8,
            location=xyz((0,.046,0)))
        finish(bpy.context.object,"White cup rim",trash,trim)
    else:
        ball("Crumpled paper",trash,(.11,.10,.09),(0,0,0),paper,True)
        box("Paper crease",trash,(.078,.004,.016),(0,.048,0),cream)

# Bake horizontal expansion into architecture, preserving fireplace proportions
# and material grain. Furniture is built separately and never stretched.
EXPANSION=1.45
for o in house.children_recursive:
    if o.type!="MESH" or o.parent==fire:continue
    if o.name.startswith(("Chimney","Plastered chimney")):
        o.location.x+=(EXPANSION-1)*(-1.018)
        o.location.y-=(EXPANSION-1)*.29
        continue
    o.location.x*=EXPANSION;o.location.y*=EXPANSION
    for v in o.data.vertices:
        v.co.x*=EXPANSION;v.co.y*=EXPANSION
    if o.data.uv_layers.active:
        for uv in o.data.uv_layers.active.data:uv.uv*=EXPANSION
fire.location.x*=EXPANSION;fire.location.y*=EXPANSION
for name,r in roots.items():
    if any(token in name for token in ("_wipe-","_cell-","_piece-")):continue
    if name.startswith(("clean_","dust-","debris-","wall-","trash-")):
        r.location.x*=EXPANSION;r.location.y*=EXPANSION
        if name=="clean_stand":
            for child in r.children:
                child.location.x*=EXPANSION;child.location.y*=EXPANSION
        elif name.startswith(("dust-","debris-","wall-")):
            for child in r.children_recursive:
                child.location.x*=EXPANSION
                if not name.startswith("wall-"):child.location.y*=EXPANSION
                if child.type=="MESH" and not name.startswith("debris-"):
                    for v in child.data.vertices:
                        v.co.x*=EXPANSION
                        if not name.startswith("wall-"):v.co.y*=EXPANSION

# Merge static meshes per independently addressed root. Tools/mess stay separate.
atelier.apply()
# These quiet painted/mineral finishes retain their broad albedo and roughness.
# Their fine normal maps alias strongly on the current emulator with SDK 6.1.9,
# including at authored strength; remove only those maps from the shipped model.
for m in bpy.data.materials:
    if m.name in {"Atelier | Warm mineral plaster","Atelier | Olive mineral paint",
                  "Atelier | Ivory painted joinery","Atelier | Lime mortar"}:
        bs=m.node_tree.nodes.get("Principled BSDF")
        for link in list(bs.inputs["Normal"].links): m.node_tree.links.remove(link)
for parent in list(roots.values())[::-1]:
    meshes=[o for o in parent.children if o.type=="MESH"]
    if not meshes: continue
    bpy.ops.object.select_all(action="DESELECT")
    for o in meshes:o.select_set(True)
    bpy.context.view_layer.objects.active=meshes[0]
    if len(meshes)>1:bpy.ops.object.join()
    o=meshes[0];o.name=parent.name+"_mesh"
    # Animated bristles have their own root. Bake the joined mesh's initial
    # cylinder rotation so its native bounds remain aligned with that root.
    bpy.ops.object.transform_apply(location=False,rotation=True,scale=True)
    mod=o.modifiers.new("Export triangles","TRIANGULATE");bpy.ops.object.modifier_apply(modifier=mod.name)
bpy.context.view_layer.update()
manifest={}
for name,root in roots.items():
    coords=[root.matrix_world.inverted()@o.matrix_world@Vector(c) for o in root.children_recursive if o.type=="MESH" for c in o.bound_box]
    if not coords:continue
    pico=[(p.x,p.z,-p.y) for p in coords]
    manifest[name]={"min":[min(p[k] for p in pico) for k in range(3)],"max":[max(p[k] for p in pico) for k in range(3)],
        "position":[root.location.x,root.location.z,-root.location.y]}
(OUT/"manifest.json").write_text(json.dumps(manifest,indent=2))
bpy.ops.object.select_all(action="SELECT")
bpy.ops.export_scene.gltf(filepath=str(ASSETS/"tidy-renovation.glb"),export_format="GLB",
    export_yup=True,export_apply=True,export_tangents=True,export_animations=False)
if "--export-only" in sys.argv:
    print("TIDY_RENOVATION_EXPORT_COMPLETE",flush=True)
    sys.exit(0)
world=bpy.context.scene.world;world.use_nodes=True
world.node_tree.nodes["Background"].inputs[0].default_value=(.62,.67,.72,1)
world.node_tree.nodes["Background"].inputs[1].default_value=.4
for p,power,size in [((0,2,1.4),180,3),((-1,1.4,-.5),80,2)]:
    light=bpy.data.lights.new("Studio softbox","AREA");light.energy=power;light.size=size
    o=bpy.data.objects.new("Studio softbox",light);bpy.context.collection.objects.link(o);o.location=xyz(p)
    o.rotation_euler=(Vector(xyz((0,.1,0)))-o.location).to_track_quat("-Z","Y").to_euler()
camera=bpy.data.objects.new("Inspection",bpy.data.cameras.new("Inspection"));bpy.context.collection.objects.link(camera)
camera.location=xyz((3.2,2.5,4.8))
camera.rotation_euler=(Vector(xyz((.10,.20,-.18)))-camera.location).to_track_quat("-Z","Y").to_euler()
camera.data.type="ORTHO";camera.data.ortho_scale=4.95
scene=bpy.context.scene;scene.camera=camera;scene.render.engine="CYCLES";scene.cycles.samples=32
scene.cycles.use_denoising=True;scene.render.resolution_x=1600;scene.render.resolution_y=1200;scene.render.resolution_percentage=100
scene.view_settings.view_transform="AgX";scene.render.filepath=str(OUT/"dirty-house.png")
atelier.warm_preview(scene)
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/"TidySpace-renovation.blend"))
bpy.ops.render.render(write_still=True)
for name,root in roots.items():
    if name.startswith(("dust-","debris-","wall-","trash-","clean_")):
        root.hide_render=True
        for child in root.children_recursive:child.hide_render=True
scene.render.filepath=str(OUT/"clean-house.png");bpy.ops.render.render(write_still=True)
print("TIDY_RENOVATION_COMPLETE",flush=True)
