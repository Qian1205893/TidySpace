"""Repair zero tangent directions in exported GLB bytes without changing geometry.

UV seams can produce zero directions in Blender's glTF export. Use a tangent
orthogonal to the existing normal at only those vertices. The fourth component
and every valid tangent remain unchanged.
"""
import json
import math
import struct
import sys
from pathlib import Path

path=Path(sys.argv[1])
data=bytearray(path.read_bytes())
length=struct.unpack_from("<I",data,12)[0]
gltf=json.loads(data[20:20+length])
base=28+length
def locate(index):
    a=gltf["accessors"][index];v=gltf["bufferViews"][a["bufferView"]]
    assert a["componentType"]==5126
    return a,base+v.get("byteOffset",0)+a.get("byteOffset",0),v.get("byteStride",16 if a["type"]=="VEC4" else 12)
repaired=0
seen=set()
for mesh in gltf["meshes"]:
    for p in mesh["primitives"]:
        attrs=p["attributes"]
        if "TANGENT" not in attrs: continue
        if attrs["TANGENT"] in seen: continue
        seen.add(attrs["TANGENT"])
        a,offset,stride=locate(attrs["TANGENT"])
        _,normalOffset,normalStride=locate(attrs["NORMAL"])
        for n in range(a["count"]):
            at=offset+n*stride
            x,y,z=struct.unpack_from("<3f",data,at)
            if x*x+y*y+z*z>.0001: continue
            nx,ny,nz=struct.unpack_from("<3f",data,normalOffset+n*normalStride)
            tangent=(-ny,nx,0) if abs(nz)<.9 else (0,-nz,ny)
            length=math.sqrt(sum(c*c for c in tangent))
            tangent=tuple(c/length for c in tangent)
            struct.pack_into("<3f",data,at,*tangent)
            repaired+=1
if repaired: path.write_bytes(data)
print(json.dumps({"file":str(path),"zeroTangentsRepaired":repaired,"geometryUnchanged":True}))
