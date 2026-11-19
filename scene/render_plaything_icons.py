"""Render the exact shipped GLBs as transparent native UI thumbnails."""
import bpy, json
from pathlib import Path
from mathutils import Vector

HERE=Path(__file__).resolve().parent
OUT=HERE.parent/"app/src/main/res/drawable"
for folder in ("playthings","toys"):
    bpy.ops.object.select_all(action="SELECT");bpy.ops.object.delete(use_global=False)
    bpy.ops.import_scene.gltf(filepath=str(HERE.parent/"app/src/main/assets/models"/f"tidy-{folder}.glb"))
    manifest=json.loads((HERE/folder/"manifest.json").read_text())
    scene=bpy.context.scene
    scene.render.engine="CYCLES"
    scene.world.use_nodes=True
    scene.world.node_tree.nodes["Background"].inputs[0].default_value=(.70,.76,.82,1)
    scene.world.node_tree.nodes["Background"].inputs[1].default_value=.45
    for name,p,power in (("Key",(-.35,-.5,.7),24),("Fill",(.4,-.1,.5),10)):
        light=bpy.data.lights.new(name,"AREA");light.energy=power;light.size=.55
        o=bpy.data.objects.new(name,light);bpy.context.collection.objects.link(o);o.location=p
        o.rotation_euler=(-o.location).to_track_quat("-Z","Y").to_euler()
    camera=bpy.data.objects.new("Icon camera",bpy.data.cameras.new("Icon camera"))
    bpy.context.collection.objects.link(camera);scene.camera=camera
    scene.view_settings.view_transform="AgX"
    scene.render.resolution_x=192;scene.render.resolution_y=192;scene.render.resolution_percentage=100
    scene.render.film_transparent=True;scene.render.image_settings.color_mode="RGBA"
    scene.cycles.samples=24;scene.cycles.use_denoising=True;scene.view_settings.exposure=-.65
    meshes=[o for o in bpy.data.objects if o.type=="MESH"]
    for name,bounds in manifest.items():
        for o in meshes:o.hide_render=True
        root=bpy.data.objects[name];root.location=(0,0,0)
        for child in root.children_recursive:
            if child.type=="MESH":child.hide_render=False
        # Thumbnail shows the recognizable closed ball, with the star saved for the interaction.
        if name=="pokeball":
            bpy.data.objects["pokeball_lid"].rotation_euler=(0,0,0)
            for child in bpy.data.objects["pokeball_star"].children_recursive:child.hide_render=True
        height=(bounds["max"][1]+bounds["min"][1])/2
        target=Vector((0,0,height))
        camera=scene.camera
        camera.location=target+Vector((.20,-.42,.28))
        camera.rotation_euler=(target-camera.location).to_track_quat("-Z","Y").to_euler()
        camera.data.type="ORTHO"
        camera.data.ortho_scale=max(bounds["max"][k]-bounds["min"][k] for k in range(3))*1.35
        scene.render.filepath=str(OUT/f"toy_{name}.png")
        bpy.ops.render.render(write_still=True)
