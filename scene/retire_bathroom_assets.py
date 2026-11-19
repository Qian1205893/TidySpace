"""Prune retired mesh resources without resampling any surviving geometry."""
import copy,json,struct,shutil
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
PRIVATE=Path("/tmp/tidyspace-study-20261116")
assets=ROOT/"app/src/main/assets/models"
path=assets/"tidy-home-expansion.glb"
raw=path.read_bytes()
backup=PRIVATE/"original-home-expansion.glb"
if not backup.exists():backup.write_bytes(raw)
n=struct.unpack_from("<I",raw,12)[0]
g=json.loads(raw[20:20+n])
data=raw[28+n:]
retired={"washer","bathvanity","toilet","bathtub","mirror","laundrybasket"}
removed=set()
def descend(i):
    removed.add(i)
    for child in g["nodes"][i].get("children",[]):descend(child)
for i,node in enumerate(g["nodes"]):
    if node.get("name") in retired:descend(i)
assert not g.get("animations") and not g.get("skins")
def subset(key,used):
    used=sorted(set(used));mapping={old:new for new,old in enumerate(used)}
    g[key]=[g[key][i] for i in used]
    return mapping
nodes=subset("nodes",set(range(len(g["nodes"])))-removed)
for node in g["nodes"]:
    if "children" in node:node["children"]=[nodes[i] for i in node["children"] if i in nodes]
for scene in g["scenes"]:scene["nodes"]=[nodes[i] for i in scene["nodes"] if i in nodes]
meshes=subset("meshes",[n["mesh"] for n in g["nodes"] if "mesh" in n])
for node in g["nodes"]:
    if "mesh" in node:node["mesh"]=meshes[node["mesh"]]
primitives=[p for mesh in g["meshes"] for p in mesh["primitives"]]
materials=subset("materials",[p["material"] for p in primitives if "material" in p])
for p in primitives:
    if "material" in p:p["material"]=materials[p["material"]]
refs=[]
def texture_refs(value):
    if isinstance(value,dict):
        for k,v in value.items():
            if k.endswith("Texture") and isinstance(v,dict) and "index" in v:refs.append(v)
            else:texture_refs(v)
    elif isinstance(value,list):
        for v in value:texture_refs(v)
texture_refs(g["materials"])
textures=subset("textures",[v["index"] for v in refs])
for v in refs:v["index"]=textures[v["index"]]
images=subset("images",[t["source"] for t in g["textures"]])
for t in g["textures"]:t["source"]=images[t["source"]]
samplers=subset("samplers",[t["sampler"] for t in g["textures"] if "sampler" in t])
for t in g["textures"]:
    if "sampler" in t:t["sampler"]=samplers[t["sampler"]]
accessors=subset("accessors",[i for p in primitives for i in
    list(p["attributes"].values())+([p["indices"]] if "indices" in p else [])])
for p in primitives:
    p["attributes"]={k:accessors[v] for k,v in p["attributes"].items()}
    if "indices" in p:p["indices"]=accessors[p["indices"]]
assert all("sparse" not in a for a in g["accessors"])
views=subset("bufferViews",[a["bufferView"] for a in g["accessors"]]+[i["bufferView"] for i in g["images"]])
for a in g["accessors"]+g["images"]:a["bufferView"]=views[a["bufferView"]]
binary=bytearray()
for view in g["bufferViews"]:
    offset=view.get("byteOffset",0);length=view["byteLength"]
    while len(binary)%4:binary.append(0)
    view["byteOffset"]=len(binary)
    binary.extend(data[offset:offset+length])
while len(binary)%4:binary.append(0)
g["buffers"]=[{"byteLength":len(binary)}]
text=json.dumps(g,separators=(",",":")).encode()
text+=b" "*((-len(text))%4)
output=struct.pack("<4sII",b"glTF",2,28+len(text)+len(binary))
output+=struct.pack("<I4s",len(text),b"JSON")+text+struct.pack("<I4s",len(binary),b"BIN\0")+binary
path.write_bytes(output)
bath=assets/"tidy-bathroom.glb"
if bath.exists():
    shutil.copy2(bath,PRIVATE/"original-bathroom.glb")
    bath.unlink()
assert not retired.intersection(n.get("name") for n in g["nodes"])
print(json.dumps({"deletedNodes":len(removed),"beforeBytes":len(raw),"afterBytes":len(output),
                  "remainingRoots":[g["nodes"][i]["name"] for i in g["scenes"][0]["nodes"]]},indent=2))
