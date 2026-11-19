"""Original TidySpace assets. Run with Blender 4.5 --background --python this_file.

All authoring helpers accept PICO coordinates: X right, Y up, Z forward, metres.
The GLB contains independently movable roots and named mechanism pivots. Texture
images are real exported PBR inputs, not Blender-only procedural shader nodes.
"""
import bpy
import json
import math
import random
import sys
from pathlib import Path
import numpy as np
from mathutils import Vector

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import art_direction as atelier
OUT = HERE / "furniture"
ASSETS = HERE.parent / "app/src/main/assets/models"
OUT.mkdir(exist_ok=True)
ASSETS.mkdir(exist_ok=True)
random.seed(23)
bpy.ops.object.select_all(action="SELECT")
bpy.ops.object.delete(use_global=False)


def xyz(p):
    return (p[0], -p[2], p[1])


def linear(rgb):
    return tuple(c / 12.92 if c <= .04045 else ((c + .055) / 1.055) ** 2.4 for c in rgb)


def rgb(hex_value):
    return tuple(int(hex_value[i:i + 2], 16) / 255 for i in (0, 2, 4))


def image(name, data, color=True):
    h, w = data.shape[:2]
    img = bpy.data.images.new(name, w, h, alpha=False)
    img.colorspace_settings.name = "sRGB" if color else "Non-Color"
    rgba = np.ones((h, w, 4), dtype=np.float32)
    rgba[:, :, :3] = np.clip(data, 0, 1)
    # Generated-image PNG saves preserve these encoded values. The material
    # image loader performs sRGB decoding; converting again here darkens it.
    img.pixels.foreach_set(rgba.ravel())
    img.filepath_raw = str(OUT / (name + ".png"))
    img.file_format = "PNG"
    img.save()
    img.pack()
    return img


def maps(name, tone, kind, n=1024):
    y, x = np.mgrid[0:n, 0:n] / n
    rng = np.random.default_rng(1984)
    noise = rng.normal(0, 1, (n, n))
    if kind == "wood":
        warp = y + .012 * np.sin(x * 2 * math.pi) + .004 * np.sin(x * 8 * math.pi)
        grain = np.sin(warp * math.pi * 110 + np.sin(warp * 25) * 2)
        pores = np.maximum(0, np.sin(warp * math.pi * 400 + np.sin(x * 15))) ** 14
        shade = .012 * grain - .008 * pores + .003 * noise + .012 * np.sin(warp * 35)
        height = .025 * grain - .018 * pores
        rough = .46 + .025 * grain + .012 * noise
    elif kind in ("fabric", "rug"):
        weave = np.sin(x * math.pi * 180) * np.cos(y * math.pi * 180)
        shade = .021 * weave + .006 * noise
        height = .17 * weave
        rough = .88 + .035 * weave
    else:
        shade = .008 * noise + .012 * np.sin(x * 15) * np.sin(y * 17)
        height = .025 * noise
        rough = (.22 if kind == "glaze" else .73) + .025 * noise
    base = np.array(rgb(tone))[None, None, :] + shade[:, :, None]
    if kind == "rug":
        border = (np.minimum(x, 1 - x) < .055) | (np.minimum(y, 1 - y) < .055)
        stripe = ((np.minimum(x, 1 - x) > .07) & (np.minimum(x, 1 - x) < .077)) | (
            (np.minimum(y, 1 - y) > .07) & (np.minimum(y, 1 - y) < .077))
        diamond = np.abs(((x * 8) % 1) - .5) + np.abs(((y * 7) % 1) - .5)
        motif = (diamond < .065) & ~border
        base[border] = np.array(rgb("D6C7A6")) + shade[border, None]
        base[stripe | motif] = np.array(rgb("C2B894")) + shade[stripe | motif, None]
    gy, gx = np.gradient(height)
    normal = np.stack((-gx * 5, -gy * 5, np.ones_like(x)), axis=-1)
    normal /= np.linalg.norm(normal, axis=-1, keepdims=True)
    return (image(name + "_base", base), image(name + "_normal", normal * .5 + .5, False),
            image(name + "_rough", np.repeat(np.clip(rough, 0, 1)[:, :, None], 3, axis=2), False))


def material(name, tone, rough=.5, metal=0, texture=None, sheen=0, coat=0, emission=0):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    bs = m.node_tree.nodes.get("Principled BSDF")
    bs.inputs["Base Color"].default_value = (*linear(rgb(tone)), 1)
    bs.inputs["Roughness"].default_value = rough
    bs.inputs["Metallic"].default_value = metal
    bs.inputs["Sheen Weight"].default_value = sheen
    bs.inputs["Coat Weight"].default_value = coat
    bs.inputs["Coat Roughness"].default_value = .28
    if emission:
        bs.inputs["Emission Color"].default_value = (*linear(rgb(tone)), 1)
        bs.inputs["Emission Strength"].default_value = emission
    if texture:
        for img, slot in zip(texture, ("Base Color", "Normal", "Roughness")):
            tex = m.node_tree.nodes.new("ShaderNodeTexImage")
            tex.image = img
            if slot == "Normal":
                nm = m.node_tree.nodes.new("ShaderNodeNormalMap")
                nm.inputs["Strength"].default_value = .45
                m.node_tree.links.new(tex.outputs["Color"], nm.inputs["Color"])
                m.node_tree.links.new(nm.outputs["Normal"], bs.inputs[slot])
            else:
                m.node_tree.links.new(tex.outputs["Color"], bs.inputs[slot])
    return m


oak = material("Honey oak | fine open grain", "C5A174", texture=maps("oak", "C5A174", "wood"), coat=.16)
walnut = material("Warm end grain", "806147", .52)
linen = material("Oat boucle | woven normal", "DCD2BD", texture=maps("linen", "DCD2BD", "fabric"), sheen=.22)
piping = material("Linen seam cord", "BDB09A", .85)
claycloth = material("Terracotta cushion | woven normal", "C18B69", texture=maps("cushion", "C18B69", "fabric"), sheen=.25)
sage = material("Sage wool | woven border", "7F9688", texture=maps("rug", "7F9688", "rug"), sheen=.2)
ceramic = material("Celadon glaze", "ABC3B7", texture=maps("glaze", "ABC3B7", "glaze", 512), coat=.3)
terra = material("Unglazed terracotta", "B98062", texture=maps("terracotta", "B98062", "clay", 512))
brass = material("Brushed champagne brass", "B49A67", .32, .78)
iron = material("Powder coated charcoal", "3E504A", .52, .2)
paper = material("Warm paper", "E8DECB", .9)
green = material("Bookcloth forest", "526C61", .78, sheen=.15)
soil = material("Potting soil", "443A2B", 1)
leaf = material("Leaf jade", "4C8053", .43, coat=.15)
leaflight = material("Leaf young green", "80A363", .48)
vein = material("Leaf veins", "A2B97A", .55)
tea = material("Tea", "685238", .15, coat=.5)
glow = material("Warm LED", "FFE1AD", .6, emission=2)


def empty(name, parent=None, p=(0, 0, 0)):
    o = bpy.data.objects.new(name, None)
    bpy.context.collection.objects.link(o)
    o.parent = parent
    o.location = xyz(p)
    return o


def finish(o, name, parent, mat):
    o.name = name
    o.parent = parent
    o.data.materials.append(mat)
    for poly in o.data.polygons:
        poly.use_smooth = True
    return o


def uv_project(o, tile=.22):
    uv = o.data.uv_layers.new(name="UVMap") if not o.data.uv_layers else o.data.uv_layers[0]
    for face in o.data.polygons:
        a = max(range(3), key=lambda k: abs(face.normal[k]))
        axes = ((1, 2), (0, 2), (0, 1))[a]
        for i in face.loop_indices:
            p = o.data.vertices[o.data.loops[i].vertex_index].co
            uv.data[i].uv = (p[axes[0]] / tile, p[axes[1]] / tile)


def box(name, parent, size, p, mat=oak, bevel=.002):
    bpy.ops.mesh.primitive_cube_add(size=1, location=xyz(p))
    o = bpy.context.object
    o.dimensions = (size[0], size[2], size[1])
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    if bevel:
        mod = o.modifiers.new("Soft milled edge", "BEVEL")
        mod.width = min(bevel, min(size) * .45)
        mod.segments = 3
        bpy.ops.object.modifier_apply(modifier=mod.name)
    uv_project(o)
    mod = o.modifiers.new("Weighted corner normals", "WEIGHTED_NORMAL")
    mod.keep_sharp = True
    bpy.ops.object.modifier_apply(modifier=mod.name)
    return finish(o, name, parent, mat)


def mesh(name, parent, points, faces, mat, uv=None):
    data = bpy.data.meshes.new(name)
    data.from_pydata([xyz(p) for p in points], [], faces)
    data.update()
    o = bpy.data.objects.new(name, data)
    bpy.context.collection.objects.link(o)
    if uv:
        layer = data.uv_layers.new(name="UVMap")
        for face in data.polygons:
            for i in face.loop_indices:
                layer.data[i].uv = uv[data.loops[i].vertex_index]
    else:
        uv_project(o)
    return finish(o, name, parent, mat)


def tube(name, parent, points, radius, mat=oak, closed=False):
    curve = bpy.data.curves.new(name, "CURVE")
    curve.dimensions = "3D"
    curve.resolution_u = 1
    curve.bevel_depth = radius
    curve.bevel_resolution = 2
    curve.resolution_u = 1
    sp = curve.splines.new("POLY")
    sp.points.add(len(points) - 1)
    for p, co in zip(sp.points, points):
        p.co = (*xyz(co), 1)
    sp.use_cyclic_u = closed
    o = bpy.data.objects.new(name, curve)
    bpy.context.collection.objects.link(o)
    bpy.context.view_layer.objects.active = o
    o.select_set(True)
    bpy.ops.object.convert(target="MESH")
    o.select_set(False)
    uv_project(o)
    return finish(o, name, parent, mat)


def lathe(name, parent, profile, mat=oak, p=(0, 0, 0), segments=48, pleats=0):
    points, faces, uv = [], [], []
    for j, (r, y) in enumerate(profile):
        for i in range(segments + 1):
            a = i * math.tau / segments
            rr = r + pleats * math.cos(a * 32)
            points.append((p[0] + rr * math.cos(a), p[1] + y, p[2] + rr * math.sin(a)))
            uv.append((i / segments, j / (len(profile) - 1)))
    for j in range(len(profile) - 1):
        for i in range(segments):
            a = j * (segments + 1) + i
            b = a + segments + 1
            faces.append((a, b, b + 1, a + 1))
    return mesh(name, parent, points, faces, mat, uv)


def rod(name, parent, a, b, r1=.006, r2=.004, mat=oak):
    delta = Vector(xyz(b)) - Vector(xyz(a))
    bpy.ops.mesh.primitive_cone_add(vertices=16, radius1=r1, radius2=r2, depth=delta.length,
                                   location=(Vector(xyz(a)) + Vector(xyz(b))) / 2)
    o = bpy.context.object
    o.rotation_mode = "QUATERNION"
    o.rotation_quaternion = delta.to_track_quat("Z", "Y")
    uv_project(o)
    return finish(o, name, parent, mat)


def spow(t, n):
    return math.copysign(abs(t) ** n, t)


def pillow(name, parent, size, p, mat=linen, exponent=.38, seam=True):
    points, uv, faces = [], [], []
    nu, nv = 48, 24
    for j in range(nv + 1):
        v = -math.pi / 2 + math.pi * j / nv
        for i in range(nu + 1):
            u = math.tau * i / nu
            xx = spow(math.cos(v), exponent) * spow(math.cos(u), exponent)
            zz = spow(math.cos(v), exponent) * spow(math.sin(u), exponent)
            yy = spow(math.sin(v), .72)
            # Small tension folds near the sewn edge; large planes stay calm.
            yy *= 1 - .035 * (abs(xx) ** 6 + abs(zz) ** 6) * (1 + math.sin(u * 12) ** 2)
            points.append((p[0] + xx * size[0] / 2, p[1] + yy * size[1] / 2, p[2] + zz * size[2] / 2))
            uv.append((i / nu * 2, j / nv))
    for j in range(nv):
        for i in range(nu):
            a = j * (nu + 1) + i
            faces.append((a, a + nu + 1, a + nu + 2, a + 1))
    o = mesh(name, parent, points, faces, mat, uv)
    if seam:
        pts = [(p[0] + spow(math.cos(a), exponent) * size[0] / 2, p[1],
                p[2] + spow(math.sin(a), exponent) * size[2] / 2) for a in np.linspace(0, math.tau, 96, endpoint=False)]
        tube(name + " seam", parent, pts, .0007, piping if mat == linen else mat, True)
    return o


def tailored_pad(name,parent,size,p,mat=linen,back=False,buttons=()):
    """Dense sewn upholstery with real tuft depressions and broad tension folds."""
    points=[];faces=[];uv=[];nu,nv=72,36
    def orient(x,y,z):
        if "arm" in name.lower():
            # Arms rise into the back and slope gently toward the front.
            y-=.010*z/(size[2]/2)
            x*=.95+.05*math.cos(z/(size[2]/2)*math.pi/2)
        return (p[0]+x,p[1]-z,p[2]+y) if back else (p[0]+x,p[1]+y,p[2]+z)
    halfx,halfz=size[0]/2,size[2]/2;radius=min(.015,halfx*.65,halfz*.45)
    for side in (1,-1):
        offset=len(points)
        for j in range(nv+1):
            for i in range(nu+1):
                x=(i/nu*2-1)*halfx;z=(j/nv*2-1)*halfz
                # Rounded rectangular domain, uniformly sampled across the face.
                a=max(0,abs(x)-(halfx-radius));b=max(0,abs(z)-(halfz-radius))
                if a and b:
                    factor=max(a,b)/math.hypot(a,b)
                    x=math.copysign(halfx-radius+a*factor,x);z=math.copysign(halfz-radius+b*factor,z)
                d=min(halfx-abs(x),halfz-abs(z))
                edge=max(0,1-d/radius)
                rounding=min(radius,size[1]*.40)
                y=side*(size[1]/2-rounding+math.sqrt(max(0,rounding**2-
                    (rounding-min(d,rounding))**2)))
                if side>0:
                    y-=.00065*edge**3*math.sin(x*770+z*120)**2
                    for bx,bz in buttons:
                        dx=x-bx;dz=z-bz;r2=dx*dx+dz*dz
                        y-=.0055*math.exp(-r2/.000095)
                        angle=math.atan2(dz,dx)
                        y-=.0012*math.exp(-r2/.0008)*math.cos(angle*4)**12
                points.append(orient(x,y,z));uv.append((x/.075,z/.075))
        for j in range(nv):
            for i in range(nu):
                a=offset+j*(nu+1)+i
                face=(a,a+nu+1,a+nu+2,a+1)
                faces.append(face if side>0 else tuple(reversed(face)))
    per=(nu+1)*(nv+1)
    boundary=list(range(nu+1))+[j*(nu+1)+nu for j in range(1,nv+1)]+[
        nv*(nu+1)+i for i in range(nu-1,-1,-1)]+[j*(nu+1) for j in range(nv-1,0,-1)]
    for a,b in zip(boundary,boundary[1:]+boundary[:1]):faces.append((a,b,b+per,a+per))
    o=mesh(name,parent,points,faces,mat,uv)
    seam=[tuple((points[i][k]+points[i+per][k])/2 for k in range(3)) for i in boundary]
    tube(name+" tailored welt",parent,seam,.00048,mat,True)
    for bx,bz in buttons:
        button=lathe(name+" cloth covered button",parent,
            [(0,0),(.0025,0),(.0027,.0006),(.002,.001),(0,.0013)],mat,segments=20)
        if back: button.rotation_euler.x=math.pi/2
        button.location=xyz(orient(bx,size[1]/2-.0042,bz))
    return o


roots = {name: empty(name) for name in
         ("desk", "chair", "sofa", "coffee", "shelf", "rug", "lamp", "crate", "plant", "picture", "book", "mug", "cushion")}

# Desk: rounded slab, tapered legs, real hollow drawer and separate pull.
r = roots["desk"]
box("Solid oak top", r, (.48, .025, .25), (0, .1575, 0), bevel=.006)
for x in (-.202, .202):
    for z in (-.092, .092):
        rod("Tapered desk leg", r, (x * 1.05, -.17, z * 1.04), (x, .145, z), .0065, .011)
    box("Side apron", r, (.013, .055, .192), (x, .111, 0))
box("Back apron", r, (.413, .056, .012), (0, .111, -.096))
drawer = empty("desk_drawer", r, (0, .104, .015))
box("Drawer bottom", drawer, (.383, .006, .166), (0, -.025, 0), walnut)
for x in (-.189, .189):
    box("Drawer side", drawer, (.007, .046, .172), (x, 0, 0))
box("Drawer back", drawer, (.38, .045, .007), (0, 0, -.083))
box("Drawer front", drawer, (.394, .055, .014), (0, 0, .091), bevel=.003)
box("Recessed front panel", drawer, (.36, .034, .003), (0, 0, .099), bevel=.002)
for x in (-.024, .024):
    rod("Handle standoff", drawer, (x, 0, .1), (x, 0, .113), .0023, .0023, brass)
tube("Brass bar pull", drawer, [(-.027, 0, .114), (.027, 0, .114)], .003, brass)

# Reference-style mustard armchair: tailored shell, wood rail and slim legs.
r = roots["chair"]
box("Armchair walnut seat rail",r,(.163,.018,.151),(0,-.0375,0),walnut,.003)
tailored_pad("Mustard seat cushion",r,(.143,.042,.145),(0,-.0075,.008),linen)
tailored_pad("Mustard tailored back",r,(.158,.037,.161),(0,.061,-.066),linen,back=True,
    buttons=((- .041,0),(.041,0)))
for x in (-.076,.076):
    tailored_pad("Upholstered arm",r,(.027,.089,.158),(x,.0175,.003),linen)
    rod("Walnut arm support",r,(x,-.055,.053),(x,-.035,.053),.003,.003,walnut)
for x in (-.062, .062):
    for z in (-.057, .057):
        rod("Splayed walnut chair leg",r,(x*1.15,-.145,z*1.12),(x,-.040,z),.0037,.0068,walnut)

# Upholstery with smooth compound curvature, piping, frame reveal and button tufts.
r = roots["sofa"]
box("Recessed walnut sofa frame", r, (.39, .018, .197), (0, -.080, 0),walnut,bevel=.003)
for x in (-.165, .165):
    for z in (-.076, .076):
        rod("Splayed walnut sofa leg",r,(x*1.07,-.135,z*1.09),(x,-.078,z),.004,.007,walnut)
tailored_pad("Tufted continuous sofa back",r,(.398,.047,.184),(0,.040,-.086),
    linen,back=True,buttons=tuple((x,z) for x in (-.139,-.069,0,.069,.139) for z in (-.023,.032)))
for x in (-.093, .093):
    tailored_pad("Tailored seat cushion",r,(.182,.052,.17),(x,-.036,.013),linen)
for x in (-.191, .191):
    tailored_pad("Sofa upholstered arm",r,(.046,.146,.225),(x,-.005,0),linen)

# Oval coffee table, slim lower shelf and end-grain dowels.
r = roots["coffee"]
top = lathe("Oval table top", r, [(0, .047), (.145, .047), (.154, .051),
                                  (.155, .068), (.149, .075), (0, .075)])
for v in top.data.vertices:
    v.co.y *= .20 / .31
uv_project(top)
box("Lower magazine shelf", r, (.22, .012, .122), (0, -.035, 0), bevel=.005)
for x in (-.111, .111):
    for z in (-.063, .063):
        rod("Coffee leg", r, (x * 1.04, -.075, z), (x, .048, z), .005, .008)
        lathe("End grain dowel", r, [(0, .0752), (.002, .0752), (.002, .0755), (0, .0755)], walnut, (x, 0, z), 12)
# Thin rolled lip and a second subtle frame molding catch the raking light.
tube("Coffee top fine walnut bead",r,[(.151*math.cos(a),.055,.095*math.sin(a))
    for a in np.linspace(0,math.tau,120,endpoint=False)],.001,walnut,True)

# Open shelf without an invisible solid backing.
r = roots["shelf"]
for y in (-.119, 0, .119):
    box("Shelf board", r, (.27, .012, .11), (0, y, 0), bevel=.0025)
for x in (-.129, .129):
    box("Shelf upright", r, (.012, .238, .105), (x, 0, 0), bevel=.002)
    for y in (-.062, .062):
        box("Mounting bracket", r, (.018, .06, .007), (x, y, -.051), iron, .001)
        lathe("Brass fastener", r, [(0, 0), (.002, 0), (.002, .001), (0, .001)], brass, (x, y, -.046), 12)
for y in (-.113,.113):
    box("Shelf front molded bead",r,(.262,.004,.004),(0,y,.052),walnut,.001)

# Woven rug: exported pattern and fine normal detail, bound edge and short tassels.
r = roots["rug"]
o = box("Woven rug body", r, (.618, .006, .494), (0, 0, 0), sage, .002)
uv = o.data.uv_layers[0]
for face in o.data.polygons:
    for i in face.loop_indices:
        p = o.data.vertices[o.data.loops[i].vertex_index].co
        uv.data[i].uv = (p.x / .618 + .5, -p.y / .494 + .5)
for z in (-.252, .252):
    for i in range(63):
        x = -.303 + i * .0098
        tube("Twisted wool tassel", r, [(x, 0, z * .975), (x + .001, .001, z),
                                      (x - .0005, 0, math.copysign(.26, z))], .0007, paper)

# Pleated linen shade with inner surface, trim, brass hardware and pull chain.
r = roots["lamp"]
lathe("Lamp foot", r, [(0, -.27), (.052, -.27), (.064, -.265), (.065, -.26),
                      (.061, -.254), (.049, -.251), (0, -.251)], iron)
lathe("Lamp stem", r, [(0, -.253), (.004, -.253), (.004, .198), (0, .198)], brass, segments=24)
lathe("Pleated shade", r, [(.06, .177), (.062, .179), (.036, .267),
                         (.034, .267), (.058, .18), (.06, .177)],
      linen, segments=192, pleats=.0014)
for y, radius in ((.178, .061), (.268, .035)):
    tube("Shade bound edge", r, [(radius * math.cos(a), y, radius * math.sin(a))
                               for a in np.linspace(0, math.tau, 96, endpoint=False)], .0017, paper, True)
lathe("Shade finial", r, [(0, .257), (.006, .257), (.006, .269), (0, .27)], brass, segments=24)
bulb = empty("lamp_bulb", r)
pillow("Warm bulb diffuser", bulb, (.025, .034, .025), (0, .209, 0), glow, 1, False)
for i in range(19):
    lathe("Pull chain link", r, [(0, -.0012), (.001, 0), (0, .0012)], brass,
          (.041, .178 - i * .0045, .018), 8)
switch = empty("lamp_switch", r, (.041, .092, .018))
lathe("Pull grip", switch, [(0, -.009), (.004, -.009), (.005, 0), (.003, .009), (0, .009)], brass, segments=24)

# Crate walls and open interior; lid origin is the back hinge, not its centre.
r = roots["crate"]
box("Crate floor", r, (.25, .015, .23), (0, -.0925, 0), bevel=.002)
for z in (-.108, .108):
    for row in range(4):
        box("Crate slat", r, (.25, .044, .014), (0, -.069 + row * .047, z), bevel=.0015)
for x in (-.118, .118):
    box("Crate side", r, (.014, .186, .207), (x, -.0065, 0), bevel=.002)
    for z in (-.085, .085):
        box("Corner batten", r, (.012, .18, .02), (x * .90, -.009, z), walnut, .001)
    # Raised handhold surround and dark inner recess.
    box("Handhold recess", r, (.001, .018, .063), (math.copysign(.125, x), .038, 0), walnut, .006)
lid = empty("crate_lid", r, (0, .093, -.115))
for i in range(5):
    box("Lid plank", lid, (.049, .012, .23), (-.10 + i * .05, .001, .115), bevel=.0012)
for x in (-.075, .075):
    box("Lid brace", lid, (.016, .004, .205), (x, .009, .115), walnut, .001)
    box("Brass hinge", lid, (.024, .003, .029), (x, .008, .012), brass, .001)
    for z in (.025, .199):
        lathe("Lid nail", lid, [(0, .011), (.0016, .011), (.0016, .012), (0, .012)], iron, (x, 0, z), 12)

# Plant with rolled ceramic rim, soil granules, curved lanceolate leaves and veins.
r = roots["plant"]
lathe("Terracotta pot", r, [(0, -.08), (.023, -.08), (.026, -.077), (.034, -.029),
                          (.037, -.028), (.038, -.022), (.035, -.019), (.031, -.022),
                          (.029, -.032), (.023, -.067), (0, -.067)], terra)
lathe("Soil surface", r, [(0, -.027), (.03, -.027), (.03, -.024), (0, -.024)], soil)
for i in range(10):
    angle = i * 2.39996
    end = (.038 * math.cos(angle), .054 + .024 * (i % 3) / 2, .038 * math.sin(angle))
    start = (.004 * math.cos(angle), -.025, .004 * math.sin(angle))
    mid = (end[0] * .38, .025, end[2] * .38)
    tube("Plant stem", r, [start, mid, end], .00065, leaf)
    pts, faces, uvs = [], [], []
    centers = []
    for j in range(13):
        t = j / 12
        c = Vector(mid).lerp(Vector(end), t)
        c.y += .012 * math.sin(t * math.pi)
        centers.append(tuple(c))
        width = .012 * math.sin(t * math.pi) ** .8
        for k in (-1, 0, 1):
            pts.append((c.x + k * width * math.cos(angle + math.pi / 2),
                        c.y - abs(k) * .003 * math.sin(t * math.pi),
                        c.z + k * width * math.sin(angle + math.pi / 2)))
            uvs.append(((k + 1) / 2, t))
    for j in range(12):
        for k in range(2):
            a = j * 3 + k
            faces.append((a, a + 1, a + 4, a + 3))
    mesh("Curved leaf", r, pts, faces, leaf if i % 2 else leaflight, uvs)
    tube("Leaf midrib", r, centers, .00024, vein)
for i in range(18):
    a = random.random() * math.tau
    rr = random.random() * .026
    pillow("Soil granule", r, (.004, .002, .003), (rr * math.cos(a), -.023, rr * math.sin(a)), walnut, 1, False)

# Framed art is a true embedded image, with a paper mat and bevelled timber frame.
r = roots["picture"]
box("Frame backing", r, (.216, .166, .005), (0, 0, -.005), walnut)
for x in (-.104, .104):
    box("Frame side", r, (.012, .17, .015), (x, 0, 0), oak, .0018)
for y in (-.079, .079):
    box("Frame rail", r, (.20, .012, .015), (0, y, 0), oak, .0018)
box("Gallery mat", r, (.198, .148, .002), (0, 0, .003), paper, .0005)
n = 512
y, x = np.mgrid[0:n, 0:n] / n
art = np.broadcast_to(np.array(rgb("E6C7A5")), (n, n, 3)).copy()
art[(x - .73) ** 2 + (y - .73) ** 2 < .013] = rgb("F6E3BC")
art[y < .40 + .15 * np.sin(x * 6 + .5)] = rgb("A3ABA0")
art[y < .24 + .13 * np.sin(x * 8 + 3)] = rgb("6E8B83")
art[y < .09 + .10 * np.sin(x * 5 + 2)] = rgb("425F5A")
artmat = material("Original mountain print", "FFFFFF", .92)
tex = artmat.node_tree.nodes.new("ShaderNodeTexImage")
tex.image = image("mountain_art", art)
artmat.node_tree.links.new(tex.outputs["Color"], artmat.node_tree.nodes.get("Principled BSDF").inputs["Base Color"])
mesh("Art print", r, [(-.085, -.06, .0042), (.085, -.06, .0042), (.085, .06, .0042), (-.085, .06, .0042)],
     [(0, 1, 2, 3)], artmat, [(0, 0), (1, 0), (1, 1), (0, 1)])

# Bound journal with page edges, spine ribs, embossed bands and bookmark.
r = roots["book"]
box("Page block", r, (.103, .013, .07), (.001, 0, 0), paper, .001)
for y in (-.008, .008):
    box("Linen hardcover", r, (.11, .002, .075), (0, y, 0), green, .001)
box("Rounded spine", r, (.006, .018, .075), (-.052, 0, 0), green, .002)
for y in np.linspace(-.005, .005, 9):
    box("Fine page edge", r, (.101, .00015, .0003), (.002, y, .0351), walnut, 0)
for z in (-.026, .024):
    box("Gold spine band", r, (.0064, .018, .001), (-.052, 0, z), brass, .0003)
box("Cover stamped border", r, (.079, .0003, .048), (.004, .0091, 0), brass, .001)
box("Cover inset", r, (.075, .0004, .044), (.004, .0093, 0), green, .001)
box("Ribbon bookmark", r, (.005, .0006, .035), (.03, .004, .023), claycloth, .0002)

# Hollow mug, rounded lip, inner glaze, foot ring, tea surface and swept handle.
r = roots["mug"]
lathe("Hand thrown mug", r, [(0, -.035), (.016, -.035), (.019, -.032),
                           (.022, -.027), (.024, .027), (.0237, .033), (.022, .035),
                           (.0203, .033), (.0205, .027), (.018, -.025), (0, -.025)], ceramic, p=(-.007, 0, 0), segments=64)
lathe("Tea surface", r, [(0, .017), (.020, .017), (.020, .0175), (0, .0175)], tea, p=(-.007, 0, 0), segments=48)
tube("Swept ceramic handle", r, [(.013 + .015 * math.sin(a), .002 + .020 * math.cos(a), 0)
                               for a in np.linspace(0, math.pi, 36)], .004, ceramic)
tailored_pad("Woven lumbar cushion",roots["cushion"],(.118,.041,.103),(0,0,0),claycloth,
    buttons=((- .03,0),(.03,0)))

# Join by articulation group (one multi-material mesh per static body/pivot).
# Keep leaf/node count bounded without baking the mechanisms into the furniture.
atelier.apply()
for parent in list(roots.values()) + [drawer, lid, bulb, switch]:
    objects = [o for o in parent.children if o.type == "MESH"]
    if not objects:
        continue
    bpy.ops.object.select_all(action="DESELECT")
    for o in objects:
        o.select_set(True)
    bpy.context.view_layer.objects.active = objects[0]
    if len(objects) > 1:
        bpy.ops.object.join()
    objects[0].name = parent.name + "_mesh"
    bpy.ops.object.transform_apply(location=False, rotation=True, scale=True)
    # Explicit triangles allow Blender to export stable MikkTSpace tangents.
    mod = objects[0].modifiers.new("Export triangles", "TRIANGULATE")
    bpy.ops.object.modifier_apply(modifier=mod.name)

bpy.context.view_layer.update()
manifest = {"units": "metres", "up": "Y", "mechanisms": ["desk_drawer", "crate_lid", "lamp_bulb", "lamp_switch"], "items": {}}
for name, root in roots.items():
    coords = []
    for child in root.children_recursive:
        if child.type == "MESH":
            coords.extend([root.matrix_world.inverted() @ child.matrix_world @ Vector(c) for c in child.bound_box])
    p = np.array([(c.x, c.z, -c.y) for c in coords])
    manifest["items"][name] = {"min": p.min(axis=0).tolist(), "max": p.max(axis=0).tolist()}
(OUT / "manifest.json").write_text(json.dumps(manifest, indent=2))

bpy.ops.object.select_all(action="DESELECT")
for root in roots.values():
    root.select_set(True)
    for child in root.children_recursive:
        child.select_set(True)
bpy.ops.export_scene.gltf(filepath=str(ASSETS / "tidy-furniture.glb"), export_format="GLB",
                          use_selection=True, export_apply=True, export_yup=True,
                          export_texcoords=True, export_normals=True, export_tangents=True,
                          export_animations=False, export_cameras=False, export_lights=False)
if "--export-only" in sys.argv:
    print("TIDY_FURNITURE_EXPORT_COMPLETE",flush=True)
    sys.exit(0)

# Editable source is saved in a staged inspection layout. GLB roots above remain zero.
layout = {
    "desk": (-.36, -.15, -.43), "chair": (-.34, -.175, -.02),
    "sofa": (.34, -.185, -.43), "coffee": (.30, -.245, -.08),
    "shelf": (.47, .17, -.557), "rug": (.30, -.316, -.29), "lamp": (-.58, -.05, .22),
    "crate": (.49, -.22, .24), "picture": (.065, .28, -.606),
    "plant": (-.49, .1, -.46), "book": (.25, -.16, -.09),
    "mug": (-.28, .055, -.40), "cushion": (.36, -.192, -.40),
}
for name, p in layout.items():
    roots[name].location = xyz(p)
drawer.location = xyz((0, .104, .065))
lid.rotation_euler.x = math.radians(-105)
floor = box("Preview floor only", None, (1.5, .06, 1.12), (0, -.35, -.12), oak, .006)
wallmat = material("Preview warm plaster", "E1D5BD", .88)
box("Preview back wall only", None, (1.5, 1.04, .04), (0, .2, -.67), wallmat, .001)

world = bpy.context.scene.world
world.use_nodes = True
world.node_tree.nodes["Background"].inputs[0].default_value = (.65, .72, .78, 1)
world.node_tree.nodes["Background"].inputs[1].default_value = .35

def light_at(name, p, power, size, color, target=(0, -.05, -.12)):
    data = bpy.data.lights.new(name, "AREA")
    data.energy, data.shape, data.size, data.color = power, "DISK", size, color
    o = bpy.data.objects.new(name, data)
    bpy.context.collection.objects.link(o)
    o.location = xyz(p)
    o.rotation_euler = (Vector(xyz(target)) - o.location).to_track_quat("-Z", "Y").to_euler()

light_at("Large soft window", (-1.2, 1.6, .5), 115, 1.1, (1, .88, .72))
light_at("Cool bounce", (1.2, .9, .1), 60, 1.2, (.78, .87, 1))
cam = bpy.data.cameras.new("Asset inspection camera")
camera = bpy.data.objects.new("Asset inspection camera", cam)
bpy.context.collection.objects.link(camera)
camera.location = xyz((1.50, 1.1, 2.15))
camera.rotation_euler = (Vector(xyz((0, .05, -.16))) - camera.location).to_track_quat("-Z", "Y").to_euler()
cam.type, cam.ortho_scale = "ORTHO", 1.95
scene = bpy.context.scene
scene.camera = camera
scene.render.engine = "CYCLES"
scene.cycles.samples = 40
scene.cycles.use_denoising = True
scene.render.resolution_x, scene.render.resolution_y, scene.render.resolution_percentage = 1600, 1300, 100
scene.view_settings.view_transform = "AgX"
scene.render.image_settings.file_format = "PNG"
scene.render.filepath = str(OUT / "blender-inspection.png")
atelier.warm_preview(scene)
bpy.ops.wm.save_as_mainfile(filepath=str(OUT / "TidySpace-furniture.blend"))
bpy.ops.render.render(write_still=True)
print("TIDY_FURNITURE_EXPORT_COMPLETE", flush=True)
