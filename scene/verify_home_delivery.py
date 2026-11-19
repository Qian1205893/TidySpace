"""Semantic inventory-upgrade evidence. Existing rows/stock/counters must survive.

backup is called immediately before every install. check permits only 30 new
catalogue rows appended to a current save and their corresponding box stock.
"""
import hashlib
import json
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/"play-home-expansion-20261115"
PRIVATE=Path("/tmp/tidyspace-play-home-20261115")
ADB="/opt/homebrew/bin/adb"
PACKAGE="com.example.tidyspace.pc5853b2a"
mode,device,label=sys.argv[1:4]
def run(*args): return subprocess.check_output([ADB,"-s",device,*args])
def save(): return run("exec-out","run-as",PACKAGE,"cat","shared_prefs/tidy-room.xml")
def digest(data): return hashlib.sha256(data).hexdigest()
def state(data):
    strings={n.attrib["name"]:n.text or "" for n in ET.fromstring(data) if n.tag=="string"}
    room=next(v for v in strings.values() if v.startswith("tidy-room-"))
    rows=[r.split("\t") for r in room.strip().splitlines()]
    return rows[0],{r[0]:r for r in rows[1:]}
path=PRIVATE/(device+"-"+label+".xml")
if mode=="backup":
    value=save();path.write_bytes(value)
    print(json.dumps({"device":device,"label":label,"saveSHA256":digest(value)}))
elif mode=="check":
    sceneReady=False
    deadline=time.monotonic()+150
    logs=""
    pid=""
    while time.monotonic()<deadline:
        try:
            pid=run("shell","pidof",PACKAGE).decode().strip()
            logs=run("logcat","-d","--pid",pid,"-v","brief","TidyRoom:I","TidyAssets:I",
                     "TidyGrab:I","AndroidRuntime:E","*:S").decode()
            sceneReady="ready furnishings=" in logs
            if sceneReady: break
        except subprocess.CalledProcessError: pass
        time.sleep(.5)
    before=path.read_bytes();after=save()
    (PRIVATE/(device+"-"+label+"-after.xml")).write_bytes(after)
    a,old=state(before);b,new=state(after)
    allowed=set(json.loads((ROOT/"scene/home-expansion/manifest.json").read_text()))
    missing=set(old)-set(new)
    # Quaternion normalisation during decode is permitted only at float rounding precision.
    changed=[]
    for key,row in old.items():
        current=new.get(key)
        if current is None: continue
        if row[0]!=current[0] or row[8:]!=current[8:] or any(abs(float(row[i])-float(current[i]))>1e-6 for i in range(1,8)):
            changed.append(key)
    additions=set(new)-set(old)
    headersEqual=all(x==y or (i in (3,4,16,17) and abs(float(x)-float(y))<1e-6)
                     for i,(x,y) in enumerate(zip(a,b)) if i!=15) and len(a)==len(b)
    def stocks(header):
        return dict((v.split(":")[0],v.split(":")[1].split(",") if v.split(":")[1] else [])
                    for v in header[15].split(";"))
    stockA,stockB=stocks(a),stocks(b)
    stockPreserved=set(stockA)==set(stockB) and all(
        [v for v in stockB[k] if v not in additions]==stockA[k] for k in stockA)
    stockNew=sorted(v for values in stockB.values() for v in values if v in additions)
    appendOnly=additions<=allowed and all(new[k][9]=="true" and new[k][8]=="-" and new[k][10]==k for k in additions)
    apk=ROOT/"app/build/outputs/apk/debug/app-debug.apk"
    apkpath=next(v[8:] for v in run("shell","pm","path",PACKAGE).decode().splitlines() if v.endswith("/base.apk"))
    actual=run("shell","sha256sum",apkpath).decode().split()[0]
    (OUT/(device+"-runtime.log")).write_text(logs)
    result={"device":device,"pid":pid,"ready":sceneReady,"apkMatches":actual==digest(apk.read_bytes()),
        "saveSHA256":digest(after),"oldRows":len(old),"newRows":len(new),"addedRows":sorted(additions),
        "missingOldRows":sorted(missing),"changedOldRows":changed,"oldHeaderPreserved":headersEqual,
        "oldStockAndOrderPreserved":stockPreserved,"newStockMatchesNewRows":stockNew==sorted(additions),
        "appendOnly":appendOnly,"fatal":"FATAL EXCEPTION" in logs,
        "sceneFailure":any(s in logs for s in ("scene-load-failed","scene-update-failed","assets-failed"))}
    result["savePreserved"]=not missing and not changed and headersEqual and stockPreserved and appendOnly
    (OUT/(device+"-"+label+"-delivery.json")).write_text(json.dumps(result,indent=2))
    (OUT/(device+"-delivery.json")).write_text(json.dumps(result,indent=2))
    print(json.dumps(result,indent=2))
    assert result["savePreserved"] and result["newStockMatchesNewRows"]
    assert result["ready"] and result["apkMatches"] and not result["fatal"] and not result["sceneFailure"]
else: raise ValueError("Expected backup or check")
