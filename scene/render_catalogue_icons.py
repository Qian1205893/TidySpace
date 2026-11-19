"""Transparent catalogue artwork rendered from the exact runtime model families."""
import bpy
from pathlib import Path
from mathutils import Vector

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app/src/main/res/drawable"
for family in ("furniture", "moving-home", "home-expansion", "toys", "playthings", "gaming", "study"):
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete(use_global=False)
    bpy.ops.import_scene.gltf(filepath=str(ROOT / "app/src/main/assets/models" / f"tidy-{family}.glb"))
    roots = [o for o in bpy.data.objects if o.parent is None and
             any(c.type == "MESH" for c in [o] + list(o.children_recursive))]
    meshes = [o for o in bpy.data.objects if o.type == "MESH"]
    scene = bpy.context.scene
    scene.render.engine = "CYCLES"
    scene.cycles.samples = 16
    scene.cycles.use_denoising = True
    scene.world.use_nodes = True
    scene.world.node_tree.nodes["Background"].inputs[0].default_value = (.78, .73, .63, 1)
    scene.world.node_tree.nodes["Background"].inputs[1].default_value = .6
    for name, p, power in (("Key", (-1, -1.4, 2), 95), ("Fill", (1.4, -.7, 1), 45)):
        light = bpy.data.lights.new(name, "AREA")
        light.energy = power
        light.size = 2
        obj = bpy.data.objects.new(name, light)
        bpy.context.collection.objects.link(obj)
        obj.location = p
        obj.rotation_euler = (-obj.location).to_track_quat("-Z", "Y").to_euler()
    camera = bpy.data.objects.new("Icon camera", bpy.data.cameras.new("Icon camera"))
    bpy.context.collection.objects.link(camera)
    scene.camera = camera
    camera.data.type = "ORTHO"
    scene.view_settings.view_transform = "AgX"
    scene.view_settings.exposure = -.2
    scene.render.resolution_x = 256
    scene.render.resolution_y = 256
    scene.render.resolution_percentage = 100
    scene.render.film_transparent = True
    scene.render.image_settings.color_mode = "RGBA"
    for root in roots:
        if root.name.startswith("movingbox") or root.name == "crate":
            continue
        for mesh in meshes:
            mesh.hide_render = True
        root.location = (0, 0, 0)
        visible = [o for o in [root] + list(root.children_recursive) if o.type == "MESH"]
        for mesh in visible:
            mesh.hide_render = False
        bpy.context.view_layer.update()
        points = [o.matrix_world @ Vector(c) for o in visible for c in o.bound_box]
        lo = Vector(tuple(min(p[k] for p in points) for k in range(3)))
        hi = Vector(tuple(max(p[k] for p in points) for k in range(3)))
        target = (lo + hi) / 2
        camera.location = target + Vector((.3, -.8, .46))
        camera.rotation_euler = (target - camera.location).to_track_quat("-Z", "Y").to_euler()
        projected = [camera.rotation_euler.to_matrix().transposed() @ (p - target) for p in points]
        camera.data.ortho_scale = max(
            max(p.x for p in projected) - min(p.x for p in projected),
            max(p.y for p in projected) - min(p.y for p in projected)) * 1.16
        safe = root.name.lower().replace("-", "_")
        scene.render.filepath = str(OUT / f"furniture_{safe}.png")
        bpy.ops.render.render(write_still=True)
