"""Read-only save preservation and native regression receipt for the annex."""
import hashlib
import json
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
PRIVATE=Path("/tmp/tidyspace-bathroom-20261115")
OUT=ROOT.parent/"TidySpace-bathroom-expansion-20261115"
PACKAGE="com.example.tidyspace.pc5853b2a"
ADB="/opt/homebrew/bin/adb"
device=sys.argv[1]
def save():
    return subprocess.check_output([ADB,"-s",device,"exec-out","run-as",PACKAGE,
        "cat","shared_prefs/tidy-room.xml"])
def snapshot(raw):
    return next(e.text for e in ET.fromstring(raw) if e.attrib.get("name")=="snapshot")
def compare(original,current):
    before=snapshot(original).strip().splitlines()
    after=snapshot(current).strip().splitlines()
    old=before[0].split("\t");new=after[0].split("\t")
    assert new[0]=="tidy-room-12" and new[21]=="1"
    assert old[1:]==new[1:len(old)], "Original progression/inventory fields changed"
    rows={line.split("\t")[0]:line.split("\t") for line in after[1:]}
    for line in before[1:]:
        values=line.split("\t");found=rows[values[0]]
        assert values[0]==found[0] and values[8:]==found[8:], "Item identity/support/packed changed"
        assert all(abs(float(a)-float(b))<=.000001 for a,b in zip(values[1:8],found[1:8])), "Original pose changed"
    added=set(rows)-{line.split("\t")[0] for line in before[1:]}
    assert added=={"bathroom-"+id for id in ("bathtub","toilet","bathvanity","washer","laundrybasket","mirror")}, added
    return {"originalRows":len(before)-1,"newFixtureRows":6,"originalProgressionPreserved":True,
        "originalPosesPreserved":True,"originalInventoryPreserved":True,"version":12}

initial=(PRIVATE/(device+"-before.xml")).read_bytes()
current=save()
(PRIVATE/(device+"-after-migration.xml")).write_bytes(current)
receipt=compare(initial,current)
receipt["device"]=device
receipt["apkSHA256"]=hashlib.sha256((ROOT/"app/build/outputs/apk/debug/app-debug.apk").read_bytes()).hexdigest()
if "--save-only" in sys.argv:
    print(json.dumps(receipt,indent=2),flush=True)
    sys.exit(0)
methods=[
    ("BathroomNativeRuntimeTest","annexLoadsAndEveryFixtureHasNativeGeometryAndIndependentBodyHits"),
    ("BathroomNativeRuntimeTest","actualBathroomModelsMoveFaceCaptionCancelAndReload"),
    ("HomePlayNativeRuntimeTest","captionFacingUsesLiveRoomYawAndResetsOnEveryDrag"),
    ("CleaningPlayNativeRuntimeTest","jellyMochiAndTumblerBodyMeshesMoveAndKeepTheirPlay"),
    ("RoomDragNativeRuntimeTest","firstLevelToolFramesMoveActualModel"),
    ("CleaningNativeRuntimeTest","dirtyHouseCleansUnlocksAndRetainsNativeResources"),
]
receipt["nativeTests"]={}
for group,method in methods:
    before=save()
    subprocess.run(["pico-cli","shell","input","keyevent","224","--device",device],
        check=True,stdout=subprocess.DEVNULL)
    result=subprocess.run([ADB,"-s",device,"shell","am","instrument","-w","-e","class",
        PACKAGE+"."+group+"#"+method,PACKAGE+".test/androidx.test.runner.AndroidJUnitRunner"],
        capture_output=True,text=True,timeout=240)
    (PRIVATE/(device+"-"+method+".log")).write_text(result.stdout+result.stderr)
    after=save()
    passed=result.returncode==0 and "OK (1 test)" in result.stdout and "FAILURES!!!" not in result.stdout
    receipt["nativeTests"][method]={"passed":passed,"saveBytesUnchanged":before==after,
        "saveSHA256":hashlib.sha256(after).hexdigest()}
    (OUT/(device+"-verification.json")).write_text(json.dumps(receipt,indent=2,ensure_ascii=False))
    print(device,method,"PASS" if passed else "FAILED","save unchanged",before==after,flush=True)
    if not passed or before!=after:
        print(result.stdout+result.stderr,flush=True)
        raise RuntimeError("Native regression or save preservation failed")
receipt["relaunchPending"]=True
(OUT/(device+"-verification.json")).write_text(json.dumps(receipt,indent=2,ensure_ascii=False))
