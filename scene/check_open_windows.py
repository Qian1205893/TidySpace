"""Verify clear rays through both shipped architectural apertures."""
import bpy
import json
import os
from pathlib import Path
from mathutils import Vector

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/os.environ.get("TIDY_CHECK_OUT","interaction-refresh-20261115")
bpy.ops.object.select_all(action="SELECT")
bpy.ops.object.delete(use_global=False)
for family in ("renovation","moving-home"):
    bpy.ops.import_scene.gltf(filepath=str(ROOT/"app/src/main/assets/models"/f"tidy-{family}.glb"))
def xyz(p): return Vector((p[0],-p[2],p[1]))
meshes=[o for o in bpy.data.objects if o.type=="MESH"]
results=[]
samples=[("back", (x,.44,-1.30),(x,.44,-1.65)) for x in (-1.06,-.75,-.40)]
samples += [("side",(-1.40,.42,z),(-1.78,.42,z)) for z in (-.76,-.23)]
for window,start,end in samples:
    origin=xyz(start);target=xyz(end);hits=[]
    for obj in meshes:
        inverse=obj.matrix_world.inverted()
        local=inverse@origin;segment=inverse@target-local
        hit,location,_,_=obj.ray_cast(local,segment.normalized(),distance=segment.length)
        if hit: hits.append(obj.name)
    results.append({"window":window,"from":start,"to":end,"occluders":hits,"open":not hits})
(OUT/"window-aperture-check.json").write_text(json.dumps(results,indent=2))
print(json.dumps(results,indent=2),flush=True)
assert all(result["open"] for result in results),"A window aperture still has backing geometry"
