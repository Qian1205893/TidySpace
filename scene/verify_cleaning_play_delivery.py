"""Back up latest preferences before replacement, then verify semantic equality."""
import hashlib
import json
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/"cleaning-play-refresh-20261115"
PRIVATE=Path("/tmp/tidyspace-cleaning-play-20261115")
ADB="/opt/homebrew/bin/adb"
PACKAGE="com.example.tidyspace.pc5853b2a"
mode,device,label=sys.argv[1:4]
def run(*args): return subprocess.check_output([ADB,"-s",device,*args])
def save(): return run("exec-out","run-as",PACKAGE,"cat","shared_prefs/tidy-room.xml")
def digest(value): return hashlib.sha256(value).hexdigest()
def state(value):
    room=next(n.text for n in ET.fromstring(value) if n.tag=="string" and (n.text or "").startswith("tidy-room-"))
    lines=room.strip().splitlines()
    return lines[0].split("\t"),{r.split("\t")[0]:r.split("\t") for r in lines[1:]}
path=PRIVATE/(device+"-"+label+".xml")
if mode=="backup":
    data=save();path.write_bytes(data)
    print(device,"latest save backed up",digest(data),flush=True)
elif mode=="check":
    started=time.monotonic()
    deadline=started+240
    logs="";pid=""
    while time.monotonic()<deadline:
        try: pid=run("shell","pidof",PACKAGE).decode().strip()
        except subprocess.CalledProcessError: pid=""
        if pid:
            # Some target logcat clients suppress valid records with tag
            # filters. Scope by PID, then filter the captured text locally.
            raw=run("logcat","-d","--pid",pid,"-v","threadtime").decode(errors="replace")
            logs="\n".join(line for line in raw.splitlines() if any(tag in line for tag in
                ("TidyRoom","TidyAssets","TidyRenovation","TidyGrab","AndroidRuntime",
                 "FATAL EXCEPTION","scene-load-failed","scene-update-failed","assets-failed")))
            if "ready furnishings=" in logs: break
        time.sleep(.4)
    before=path.read_bytes();after=save()
    a,old=state(before);b,new=state(after)
    def equal(x,y,i): return x==y or (i in (3,4,16,17) and abs(float(x)-float(y))<1e-6)
    headers=len(a)==len(b) and all(equal(x,y,i) for i,(x,y) in enumerate(zip(a,b)))
    changed=[key for key in old if key not in new or old[key][8:]!=new[key][8:] or
        any(abs(float(old[key][i])-float(new[key][i]))>1e-6 for i in range(1,8))]
    apk=ROOT/"app/build/outputs/apk/debug/app-debug.apk"
    apkPath=next(v[8:] for v in run("shell","pm","path",PACKAGE).decode().splitlines() if v.endswith("/base.apk"))
    actual=run("shell","sha256sum",apkPath).decode().split()[0]
    result={"device":device,"pid":pid,"ready":"ready furnishings=" in logs,
        "readinessWaitSeconds":round(time.monotonic()-started,2),
        "apkMatches":actual==digest(apk.read_bytes()),"saveSHA256":digest(after),
        "saveVersion":b[0],"oldRows":len(old),"newRows":len(new),
        "headerPreserved":headers,"changedRows":changed,"rowIdsPreserved":set(old)==set(new),
        "fatal":"FATAL EXCEPTION" in logs,"sceneFailure":any(v in logs for v in
            ("scene-load-failed","scene-update-failed","assets-failed"))}
    result["savePreserved"]=headers and not changed and set(old)==set(new)
    (OUT/(device+"-"+label+"-delivery.json")).write_text(json.dumps(result,indent=2))
    (OUT/(device+"-runtime.log")).write_text(logs)
    (PRIVATE/(device+"-"+label+"-after.xml")).write_bytes(after)
    print(json.dumps(result,indent=2),flush=True)
    assert result["savePreserved"] and result["ready"] and result["apkMatches"]
    assert not result["fatal"] and not result["sceneFailure"]
else: raise ValueError(mode)
