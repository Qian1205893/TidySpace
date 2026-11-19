"""Accepted front-left annex. GLB uses metre/Y-up and the existing art pipeline.

No old asset is rewritten: cleaning masks, model pivots and furniture stay intact.
"""
import bpy
import json
import math
import sys
from pathlib import Path
from mathutils import Vector

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
PRIVATE = Path("/tmp/tidyspace-bathroom-20261115")
sys.path.insert(0, str(HERE))
import art_direction as atelier

bpy.ops.object.select_all(action="SELECT")
bpy.ops.object.delete(use_global=False)
def xyz(p): return (p[0], -p[2], p[1])
def empty(name, parent=None):
    o = bpy.data.objects.new(name, None)
    bpy.context.collection.objects.link(o)
    o.parent = parent
    return o
def finish(o, name, parent, mat):
    o.name = name
    o.parent = parent
    o.data.materials.append(mat)
    return o
def box(name, parent, size, p, mat, bevel=.002):
    bpy.ops.mesh.primitive_cube_add(size=1, location=xyz(p))
    o = bpy.context.object
    o.dimensions = (size[0], size[2], size[1])
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    if bevel:
        mod = o.modifiers.new("Soft crafted edge", "BEVEL")
        mod.width = min(bevel, min(size)*.3)
        mod.segments = 3
        bpy.ops.object.modifier_apply(modifier=mod.name)
    return finish(o, name, parent, mat)
def rod(name, parent, a, b, radius, mat, vertices=16):
    a, b = Vector(xyz(a)), Vector(xyz(b))
    bpy.ops.mesh.primitive_cylinder_add(vertices=vertices, radius=radius,
        depth=(b-a).length, location=(a+b)/2)
    o = bpy.context.object
    o.rotation_euler = (b-a).to_track_quat("Z", "Y").to_euler()
    for f in o.data.polygons: f.use_smooth = True
    return finish(o, name, parent, mat)
def pbr(name, kind, tone, rough, metal=0):
    return atelier.pbr(name, kind, tone, rough, metal)

cream = pbr("Bathroom mineral ivory", "plain", "F1EBDD", .8)
grout = pbr("Bathroom recessed grout", "plain", "DAD8C6", .9)
stone = pbr("Bathroom limestone", "plain", "D6CFC0", .64)
oak = pbr("Bathroom warm oak", "wood", "A47C55", .62)
brass = pbr("Bathroom old brass", "metal", "AB8B4D", .35, .78)
ceramic = pbr("Bathroom cream ceramic", "glaze", "FCF9F2", .22)
linen = pbr("Bathroom woven linen", "cloth", "C7BDA6", .91)
green = [pbr("Bathroom olive tile "+str(i), "glaze", c, .4)
    for i, c in enumerate(("6E8061", "758669", "697B5E", "7C8B70"))]
glass = bpy.data.materials.new("Bathroom clear short glass")
glass.use_nodes = True
bs = glass.node_tree.nodes["Principled BSDF"]
bs.inputs["Base Color"].default_value = (.63, .78, .72, .16)
bs.inputs["Alpha"].default_value = .16
bs.inputs["Roughness"].default_value = .12
glass.surface_render_method = "DITHERED"
glass.use_backface_culling = False

house = empty("bathroom_house")
floor = empty("bathroom_floor", house)
left, right, back, front = -1.53265, -.10265, .899, 2.259
cx, cz = (left+right)/2, (back+front)/2
box("Annex oak foundation", floor, (1.49, .06, 1.42), (cx, -.358, cz), oak, .006)
box("Continuous bathroom tile backing", floor, (1.43, .012, 1.36), (cx, -.3305, cz), grout)
# Low relief joints; wet and dry areas remain on the same support elevation.
cols, rows = 12, 12
for i in range(cols):
    for j in range(rows):
        x = left+(i+.5)*1.43/cols
        z = back+(j+.5)*1.36/rows
        mat = green[(i*13+j*7)%4] if j>=8 else stone
        # The old cutaway shell extends 58 mm beyond the play boundary.
        # A 1.5 mm threshold finish covers that overlap without touching its GLB.
        box("Small laid floor tile", floor, (1.43/cols-.003, .003, 1.36/rows-.003),
            (x, -.320 if j==0 else -.3215, z), mat, .001)
walls = empty("bathroom_walls", house)
box("Ivory side wall", walls, (.048, .97, 1.36), (left-.024, .165, cz), cream)
box("Olive wall grout bed", walls, (.004, .47, 1.36), (left+.001, -.08, cz), grout)
for j in range(15):
    for i in range(5):
        z = back+(j+.5)*1.36/15
        box("Hand laid olive wall tile", walls, (.005, .085, 1.36/15-.003),
            (left+.0045, -.273+i*.088, z), green[(i*5+j*11)%4], .0012)
box("Bathroom oak skirting", walls, (.018, .05, 1.36), (left+.009, -.295, cz), oak)
box("Olive tile crown bead", walls, (.014, .015, 1.36), (left+.007, .17, cz), brass)
# Doorless passage: no parked leaf, rail, handle, jamb or lintel remains.
threshold = empty("bathroom_open_passage", house)
box("Continuous flush stone join", threshold, (1.43, .0015, .0295),
    (cx, -.31925, back-.01325), stone, .0001)
# Caption-side cutaway leaves every fixture reachable and the old room visible.
box("Low cutaway front sill", walls, (1.43, .036, .028), (cx, -.302, front+.014), cream)
window = empty("bathroom_open_high_window", house)
# Two slender posts hold a real empty aperture; no fake exterior or opaque plane.
for x in (-1.472, -.908):
    box("Cream high window jamb", window, (.024, .68, .026), (x, .02, front), cream)
for y in (.14, .355):
    box("Cream high window rail", window, (.588, .024, .026), (-1.19, y, front), cream)
box("High window centre mullion", window, (.012, .215, .018), (-1.19, .248, front), cream)
box("High window stone ledge", window, (.61, .018, .067), (-1.19, .122, front-.016), stone)

shower = empty("bathroom_shower", house)
sx, sz = -.38, 2.005
box("Shower tray flush", shower, (.36, .002, .36), (sx, -.3205, sz), green[1])
box("Shallow brass linear drain", shower, (.22, .001, .018), (sx, -.3185, sz+.14), brass)
for n in range(11):
    box("Drain groove", shower, (.006, .0007, .015), (sx-.09+n*.018, -.3177, sz+.14), grout, 0)
rod("Shower brass riser", shower, (sx+.14, -.28, sz+.13), (sx+.14, .39, sz+.13), .005, brass)
rod("Shower gooseneck", shower, (sx+.14, .39, sz+.13), (sx+.025, .39, sz+.13), .005, brass)
box("Brass rainfall rose", shower, (.10, .014, .075), (sx+.01, .38, sz+.13), brass)
for x in (-.025, 0, .025):
    for z in (-.02, 0, .02):
        rod("Shower rose nozzle", shower, (sx+.01+x, .367, sz+.13+z),
            (sx+.01+x, .373, sz+.13+z), .0018, grout, 8)
for y in (-.10, -.04):
    rod("Shower mixer", shower, (sx+.14, y, sz+.13), (sx+.11, y, sz+.13), .011, brass)
glass_root = empty("bathroom_short_glass", shower)
box("Clear short shower screen", glass_root, (.003, .52, .29), (sx-.182, -.058, sz+.025), glass, .0005)
for z in (sz-.12, sz+.17):
    rod("Slim glass brass edge", glass_root, (sx-.182, -.318, z), (sx-.182, .202, z), .002, brass)
rod("Glass top rail", glass_root, (sx-.182, .202, sz-.12), (sx-.182, .202, sz+.17), .002, brass)

details = empty("bathroom_fixed_details", house)
# Bath filler belongs to the architecture; movable tub has an independent body hit.
rod("Bath filler upright", details, (-.885, -.32, 1.98), (-.885, -.005, 1.98), .0045, brass)
rod("Bath filler spout", details, (-.885, -.005, 1.98), (-.975, -.005, 1.98), .004, brass)
rod("Bath filler lever", details, (-.885, -.075, 1.98), (-.885, -.075, 2.014), .003, brass)
for z in (1.325, 1.655):
    rod("Mirror warm lamp arm", details, (left+.01, .385, z), (left+.07, .385, z), .003, brass)
    box("Mirror ivory lamp diffuser", details, (.036, .065, .042), (left+.075, .365, z), ceramic, .006)
rod("Towel brass bar", details, (left+.036, -.005, 1.755), (left+.036, -.005, 1.895), .003, brass)
box("Soft folded linen towel", details, (.022, .13, .10), (left+.045, -.067, 1.825), linen, .004)
box("Oak recessed storage shelf", details, (.065, .012, .20), (left+.04, .285, 1.065), oak)
for z in (1.02, 1.11):
    box("Sage soap bottle", details, (.029, .06, .026), (left+.045, .32, z), green[1], .005)
    rod("Soap brass pump", details, (left+.045, .35, z), (left+.045, .365, z), .004, brass)

# Map new wood only; static tiles/plaster keep quiet normals without fine noise.
for o in bpy.data.objects:
    if o.type=="MESH": atelier.project(o, tile=.48)
atelier.bake_occlusion()
for parent in (floor, walls, threshold, window, shower, glass_root, details):
    meshes=[o for o in parent.children if o.type=="MESH"]
    if not meshes: continue
    bpy.ops.object.select_all(action="DESELECT")
    for o in meshes: o.select_set(True)
    bpy.context.view_layer.objects.active=meshes[0]
    bpy.ops.object.join()
    o=bpy.context.object
    o.name=parent.name+"_mesh"
    mod=o.modifiers.new("Native triangles","TRIANGULATE")
    bpy.ops.object.modifier_apply(modifier=mod.name)
bpy.context.view_layer.update()
# Write Blender -> SDK explicitly.
points=[(p.x,p.z,-p.y) for o in house.children_recursive if o.type=="MESH" for c in o.bound_box
        for p in [o.matrix_world@Vector(c)]]
manifest={"unit":"metre","up":"+Y","front":"+Z","layoutVersion":2,"bathroomVersion":1,
    "min":[min(p[k] for p in points) for k in range(3)],
    "max":[max(p[k] for p in points) for k in range(3)],
    "roots":[o.name for o in house.children],
    "triangles":sum(len(o.data.polygons) for o in house.children_recursive if o.type=="MESH")}
(PRIVATE/"bathroom-manifest.json").write_text(json.dumps(manifest,indent=2))
bpy.ops.object.select_all(action="SELECT")
destination=ROOT/"app/src/main/assets/models/tidy-bathroom.glb"
bpy.ops.export_scene.gltf(filepath=str(destination),export_format="GLB",export_yup=True,
    export_apply=True,export_tangents=True,export_animations=False)
import subprocess
subprocess.run(["/usr/bin/python3",str(HERE/"repair_degenerate_tangents.py"),str(destination)],check=True)
bpy.ops.wm.save_as_mainfile(filepath=str(PRIVATE/"bathroom.blend"))
print("BATHROOM_COMPLETE",json.dumps(manifest),flush=True)
