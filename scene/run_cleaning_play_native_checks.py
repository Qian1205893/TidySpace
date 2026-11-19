"""Isolated native methods preserve the user's latest save byte-for-byte."""
import hashlib
import json
import subprocess
import sys
from datetime import datetime
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/"cleaning-play-refresh-20261115"
PRIVATE=Path("/tmp/tidyspace-cleaning-play-20261115")
PACKAGE="com.example.tidyspace.pc5853b2a"
ADB="/opt/homebrew/bin/adb"
device=sys.argv[1]
methods=[
    ("CleaningPlayNativeRuntimeTest","jellyMochiAndTumblerBodyMeshesMoveAndKeepTheirPlay"),
    ("CleaningNativeRuntimeTest","dirtyHouseCleansUnlocksAndRetainsNativeResources"),
    ("HomePlayNativeRuntimeTest","rawToyContactsKeepWristMotionPairSqueezeAndActualAnimation"),
    ("RoomDragNativeRuntimeTest","firstLevelToolFramesMoveActualModel"),
    ("RoomDragNativeRuntimeTest","extractingPreviewPreservesCapturedNativeEntity"),
]
if len(sys.argv)>2: methods=[m for m in methods if m[1] in sys.argv[2:]]
def save(): return subprocess.check_output([ADB,"-s",device,"exec-out","run-as",PACKAGE,"cat","shared_prefs/tidy-room.xml"])
path=OUT/(device+"-native-checks.json")
results=json.loads(path.read_text()) if path.exists() else {}
for group,method in methods:
    before=save();(PRIVATE/(device+"-"+method+".xml")).write_bytes(before)
    subprocess.run(["pico-cli","shell","input","keyevent","224","--device",device],check=True,stdout=subprocess.DEVNULL)
    result=subprocess.run([ADB,"-s",device,"shell","am","instrument","-w","-e","class",
        PACKAGE+"."+group+"#"+method,PACKAGE+".test/androidx.test.runner.AndroidJUnitRunner"],
        capture_output=True,text=True,timeout=240)
    (OUT/(device+"-"+method+".log")).write_text(result.stdout+result.stderr)
    after=save()
    passed=result.returncode==0 and "OK (1 test)" in result.stdout and "FAILURES!!!" not in result.stdout
    results[method]={"passed":passed,"saveUnchanged":before==after,"device":device,
        "saveSHA256":hashlib.sha256(after).hexdigest(),
        "apkSHA256":hashlib.sha256((ROOT/"app/build/outputs/apk/debug/app-debug.apk").read_bytes()).hexdigest(),
        "completedAt":datetime.now().astimezone().isoformat()}
    path.write_text(json.dumps(results,indent=2))
    print(device,method,"PASS" if passed else "FAILED","save unchanged:",before==after,flush=True)
    if not passed or before!=after:
        print(result.stdout+result.stderr,flush=True);raise RuntimeError("Native or save check failed")
