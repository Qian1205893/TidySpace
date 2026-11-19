"""Run each ECS regression in a fresh instrumentation process with save checks."""
import hashlib
import json
import subprocess
import sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/"play-home-expansion-20261115"
PRIVATE=Path("/tmp/tidyspace-play-home-20261115")
ADB="/opt/homebrew/bin/adb"
PACKAGE="com.example.tidyspace.pc5853b2a"
device=sys.argv[1]
methods=[
    ("HomePlayNativeRuntimeTest","allCatalogueModelsHaveGeometryBodyHitsAndPlayableParts"),
    ("HomePlayNativeRuntimeTest","captionFacingUsesLiveRoomYawAndResetsOnEveryDrag"),
    ("HomePlayNativeRuntimeTest","rawToyContactsKeepWristMotionPairSqueezeAndActualAnimation"),
    ("RoomDragNativeRuntimeTest","extractingPreviewPreservesCapturedNativeEntity"),
    ("RoomDragNativeRuntimeTest","firstLevelToolFramesMoveActualModel"),
]
if len(sys.argv)>2: methods=[entry for entry in methods if entry[1] in sys.argv[2:]]
def save():
    return subprocess.check_output([ADB,"-s",device,"exec-out","run-as",PACKAGE,
        "cat","shared_prefs/tidy-room.xml"])
path=OUT/(device+"-native-regression.json")
results=json.loads(path.read_text()) if path.exists() else {}
for group,method in methods:
    before=save()
    (PRIVATE/(device+"-native-"+method+".xml")).write_bytes(before)
    subprocess.run(["pico-cli","shell","input","keyevent","224","--device",device],check=True)
    result=subprocess.run([ADB,"-s",device,"shell","am","instrument","-w","-e","class",
        PACKAGE+"."+group+"#"+method,PACKAGE+".test/androidx.test.runner.AndroidJUnitRunner"],
        capture_output=True,text=True,timeout=240)
    (OUT/(device+"-"+method+".log")).write_text(result.stdout+result.stderr)
    after=save()
    passed=result.returncode==0 and "OK (1 test)" in result.stdout and "FAILURES!!!" not in result.stdout
    results[method]={"passed":passed,"saveUnchanged":before==after,
        "saveSHA256":hashlib.sha256(after).hexdigest(),"device":device}
    path.write_text(json.dumps(results,indent=2))
    print(device,method,"PASS" if passed else "FAILED","save unchanged:",before==after,flush=True)
    if not passed or before!=after:
        print(result.stdout+result.stderr,flush=True)
        raise RuntimeError("Native regression or save preservation failed")
