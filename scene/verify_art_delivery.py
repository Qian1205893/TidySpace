"""Read-only delivery evidence and current-save backups; never restores prefs."""
import hashlib
import json
import selectors
import subprocess
import sys
import time
from pathlib import Path

PROJECT=Path(__file__).resolve().parents[1]
OUT=PROJECT/"visual-refresh-20261115"
PRIVATE=Path("/tmp/tidyspace-visual-20261115")
ADB="/opt/homebrew/bin/adb"
PACKAGE="com.example.tidyspace.pc5853b2a"
mode,name,device,label=sys.argv[1:5]
if len(sys.argv)>5: OUT=PROJECT/sys.argv[5]
def read(*args):
    return subprocess.check_output([ADB,"-s",device,*args])
def save():
    return read("exec-out","run-as",PACKAGE,"cat","shared_prefs/tidy-room.xml")
def sha(data): return hashlib.sha256(data).hexdigest()
path=PRIVATE/f"{name}-{label}.xml"
if mode=="backup":
    current=save()
    path.write_bytes(current)
    print(json.dumps({"device":device,"backup":str(path),"save_sha256":sha(current)}))
elif mode=="check":
    before=path.read_bytes()
    after=save()
    (PRIVATE/f"{name}-{label}-after.xml").write_bytes(after)
    apk=(PROJECT/"app/build/outputs/apk/debug/app-debug.apk").read_bytes()
    apkpath=next(line[8:] for line in read("shell","pm","path",PACKAGE).decode().splitlines() if line.endswith("/base.apk"))
    actual=read("shell","sha256sum",apkpath).decode().split()[0]
    pid=read("shell","pidof",PACKAGE).decode().strip()
    if not pid.isdigit(): raise RuntimeError("Application has no running process")
    tags=("TidyRoom:I","TidyVolume:I","TidyGrab:I","TidyRenovation:I","AndroidRuntime:E","*:S")
    logs=read("logcat","-d","--pid",pid,"-v","brief",*tags).decode()
    if "ready furnishings=" not in logs:
        process=subprocess.Popen([ADB,"-s",device,"logcat","--pid",pid,"-v","brief",*tags],
                                 stdout=subprocess.PIPE,stderr=subprocess.DEVNULL)
        selector=selectors.DefaultSelector()
        selector.register(process.stdout,selectors.EVENT_READ)
        deadline=time.monotonic()+150
        try:
            while time.monotonic()<deadline and "ready furnishings=" not in logs:
                for key,_ in selector.select(.25):
                    data=key.fileobj.read1(65536)
                    if data: logs+=data.decode(errors="replace")
        finally:
            selector.close();process.terminate();process.wait(timeout=5)
    crash=read("logcat","-b","crash","-d","--pid",pid,"-v","brief").decode()
    (OUT/f"{name}-runtime.log").write_text(logs)
    (OUT/f"{name}-crash.log").write_text(crash)
    result={
        "device":device,"pid":pid,"apk_sha256":sha(apk),"apk_matches":actual==sha(apk),
        "save_sha256":sha(after),"save_unchanged":before==after,
        "ready":"ready furnishings=" in logs,
        "fatal":"FATAL EXCEPTION" in logs+crash,
        "scene_failure":any(s in logs for s in ("scene-load-failed","scene-update-failed","assets-failed")),
        "real_transform_frames":"native=frame" in logs,
    }
    (OUT/f"{name}-delivery.json").write_text(json.dumps(result,indent=2))
    print(json.dumps(result,indent=2))
    assert result["apk_matches"] and result["save_unchanged"] and result["ready"]
    assert not result["fatal"] and not result["scene_failure"]
else:
    raise RuntimeError("Expected backup or check")
