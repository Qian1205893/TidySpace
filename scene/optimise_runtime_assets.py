"""Prune annex partitions and bound PBR texture memory without editing mesh data.

Run with Pillow installed. Backups and audit outputs are required arguments.
The before/after accessor audit covers positions, indices, normals, UVs, vertex
AO and articulation transforms of every surviving node.
"""
import argparse,copy,hashlib,io,json,struct
from pathlib import Path
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
REMOVED={
    "tidy-study-room":{"study_open_passage"},
    "tidy-gaming-room":{"gaming_doorless_partitions","gaming_architectural_lighting"},
}
def decode(raw):
    n=struct.unpack_from("<I",raw,12)[0]
    return json.loads(raw[20:20+n]),raw[28+n:]
def texture_refs(value):
    refs=[]
    if isinstance(value,dict):
        for key,v in value.items():
            if key.endswith("Texture") and isinstance(v,dict) and "index" in v:refs.append((key,v))
            else:refs+=texture_refs(v)
    elif isinstance(value,list):
        for v in value:refs+=texture_refs(v)
    return refs
def view_bytes(g,data,i):
    v=g["bufferViews"][i]
    return data[v.get("byteOffset",0):v.get("byteOffset",0)+v["byteLength"]]
def accessor_bytes(g,data,i):
    a=g["accessors"][i];v=g["bufferViews"][a["bufferView"]]
    width={"SCALAR":1,"VEC2":2,"VEC3":3,"VEC4":4}[a["type"]]*{5121:1,5123:2,5125:4,5126:4}[a["componentType"]]
    raw=view_bytes(g,data,a["bufferView"]);start=a.get("byteOffset",0);stride=v.get("byteStride",width)
    return b"".join(raw[start+n*stride:start+n*stride+width] for n in range(a["count"]))
def geometry(g,data,excluded):
    nodes={}
    def walk(i):
        node=g["nodes"][i]
        if node.get("name") in excluded:return
        record={k:v for k,v in node.items() if k not in ("children","mesh")}
        record["children"]=[g["nodes"][c].get("name") for c in node.get("children",[])]
        if "mesh" in node:
            record["primitives"]=[{
                "mode":p.get("mode",4),
                "indices":hashlib.sha256(accessor_bytes(g,data,p["indices"])).hexdigest(),
                "attributes":{k:hashlib.sha256(accessor_bytes(g,data,a)).hexdigest() for k,a in p["attributes"].items()},
            } for p in g["meshes"][node["mesh"]]["primitives"]]
        nodes[i]=record
        for c in node.get("children",[]):walk(c)
    for scene in g["scenes"]:
        for i in scene["nodes"]:walk(i)
    # Names are not globally unique; order through the surviving original indices is stable.
    return list(nodes.values())
def optimise(path,backup):
    raw=path.read_bytes()
    if not backup.exists():backup.write_bytes(raw)
    g,data=decode(raw);before=copy.deepcopy(g)
    assert not g.get("skins") and not g.get("animations")
    deleted=set()
    def discard(i):
        deleted.add(i)
        for c in g["nodes"][i].get("children",[]):discard(c)
    for i,node in enumerate(g["nodes"]):
        if node.get("name") in REMOVED.get(path.stem,set()):discard(i)
    def subset(key,used):
        used=sorted(set(used));mapping={old:new for new,old in enumerate(used)}
        g[key]=[g.get(key,[])[i] for i in used]
        return mapping
    nodes=subset("nodes",set(range(len(g["nodes"])))-deleted)
    for node in g["nodes"]:
        if "children" in node:node["children"]=[nodes[i] for i in node["children"] if i in nodes]
    for scene in g["scenes"]:scene["nodes"]=[nodes[i] for i in scene["nodes"] if i in nodes]
    meshes=subset("meshes",[n["mesh"] for n in g["nodes"] if "mesh" in n])
    for node in g["nodes"]:
        if "mesh" in node:node["mesh"]=meshes[node["mesh"]]
    primitives=[p for m in g["meshes"] for p in m["primitives"]]
    materials=subset("materials",[p["material"] for p in primitives if "material" in p])
    for p in primitives:
        if "material" in p:p["material"]=materials[p["material"]]
    refs=texture_refs(g["materials"])
    textures=subset("textures",[v["index"] for _,v in refs])
    for _,v in refs:v["index"]=textures[v["index"]]
    images=subset("images",[t["source"] for t in g["textures"]])
    for t in g["textures"]:t["source"]=images[t["source"]]
    samplers=subset("samplers",[t["sampler"] for t in g["textures"] if "sampler" in t])
    for t in g["textures"]:
        if "sampler" in t:t["sampler"]=samplers[t["sampler"]]
    accessors=subset("accessors",[a for p in primitives for a in list(p["attributes"].values())+[p["indices"]]])
    for p in primitives:
        p["attributes"]={k:accessors[v] for k,v in p["attributes"].items()}
        p["indices"]=accessors[p["indices"]]
    assert all("sparse" not in a for a in g["accessors"])
    roles={}
    for role,ref in refs:
        image=g["textures"][ref["index"]]["source"]
        roles.setdefault(image,set()).add(role)
    replacements={};changes=[];unique_images=[];image_map={};image_keys={}
    for i,image in enumerate(g["images"]):
        payload=view_bytes(g,data,image["bufferView"])
        bitmap=Image.open(io.BytesIO(payload));old=bitmap.size
        cap=512 if roles.get(i,set()) & {"baseColorTexture","emissiveTexture"} else 256
        if max(old)>cap:
            bitmap=bitmap.resize((max(1,old[0]*cap//max(old)),max(1,old[1]*cap//max(old))),Image.Resampling.LANCZOS)
            buf=io.BytesIO();bitmap.save(buf,format="PNG",optimize=True);payload=buf.getvalue()
        key=hashlib.sha256(payload).digest()
        if key not in image_keys:
            image_keys[key]=len(unique_images)
            unique_images.append(image)
            replacements[image["bufferView"]]=payload
        image_map[i]=image_keys[key]
        changes.append(dict(roles=sorted(roles.get(i,[])),before=list(old),after=list(bitmap.size)))
    g["images"]=unique_images
    for texture in g["textures"]:texture["source"]=image_map[texture["source"]]
    views=subset("bufferViews",[a["bufferView"] for a in g["accessors"]]+[i["bufferView"] for i in g["images"]])
    replacements={views[k]:v for k,v in replacements.items()}
    original_views={new:old for old,new in views.items()}
    for a in g["accessors"]+g["images"]:a["bufferView"]=views[a["bufferView"]]
    binary=bytearray();dedup={}
    for i,v in enumerate(g["bufferViews"]):
        payload=replacements.get(i)
        if payload is None:payload=view_bytes(before,data,original_views[i])
        # Identical images share one binary span, including exporter copies.
        key=hashlib.sha256(payload).digest() if i in replacements else None
        if key is not None and key in dedup:
            v["byteOffset"],v["byteLength"]=dedup[key];continue
        binary+=b"\0"*((-len(binary))%4)
        v["byteOffset"]=len(binary);v["byteLength"]=len(payload);binary+=payload
        if key is not None:dedup[key]=(v["byteOffset"],v["byteLength"])
    binary+=b"\0"*((-len(binary))%4)
    g["buffers"]=[{"byteLength":len(binary)}]
    # Audit all retained attribute/index bytes and transformations.
    original=geometry(before,data,REMOVED.get(path.stem,set()))
    for record in original:
        record["children"]=[c for c in record["children"] if c not in REMOVED.get(path.stem,set())]
    assert original==geometry(g,binary,set()),path.name+" mesh data changed"
    text=json.dumps(g,separators=(",",":")).encode();text+=b" "*((-len(text))%4)
    result=struct.pack("<4sII",b"glTF",2,28+len(text)+len(binary))+struct.pack("<I4s",len(text),b"JSON")+text+struct.pack("<I4s",len(binary),b"BIN\0")+binary
    path.write_bytes(result)
    def memory(g,b):
        sizes=[Image.open(io.BytesIO(view_bytes(g,b,i["bufferView"]))).size for i in g.get("images",[])]
        return round(sum(w*h*4*4/3 for w,h in sizes))
    return dict(file=path.name,beforeBytes=len(raw),afterBytes=len(result),
        beforeTextureRGBAWithMips=memory(before,data),afterTextureRGBAWithMips=memory(g,binary),
        retainedMeshDataIdentical=True,removedNodes=len(deleted),
        removedRoots=sorted(REMOVED.get(path.stem,set())),textureChanges=changes,
        sha256=hashlib.sha256(result).hexdigest())
if __name__=="__main__":
    p=argparse.ArgumentParser();p.add_argument("--backup",type=Path,required=True);p.add_argument("--report",type=Path,required=True)
    args=p.parse_args();assert args.backup.is_dir() and args.report.parent.is_dir()
    report=[optimise(path,args.backup/path.name) for path in sorted((ROOT/"app/src/main/assets/models").glob("*.glb"))]
    args.report.write_text(json.dumps(report,indent=2))
    print(json.dumps({k:sum(r[k] for r in report) for k in ("beforeBytes","afterBytes","beforeTextureRGBAWithMips","afterTextureRGBAWithMips")},indent=2))
