"""Read-only preservation audit and isolated native checks; never replace user saves."""
import hashlib,json,subprocess,sys,xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
PRIVATE=Path("/tmp/tidyspace-gaming-20261115")
OUT=ROOT.parent/"TidySpace-gaming-renovation-20261115"
PACKAGE="com.example.tidyspace.pc5853b2a"
ADB="/opt/homebrew/bin/adb"
device=sys.argv[1]
def save():
    return subprocess.check_output([ADB,"-s",device,"exec-out","run-as",PACKAGE,
        "cat","shared_prefs/tidy-room.xml"])
def lines(raw):
    return next(e.text for e in ET.fromstring(raw) if e.get("name")=="snapshot").strip().splitlines()
before=lines((PRIVATE/(device+"-preinstall.xml")).read_bytes())
raw=save();(PRIVATE/(device+"-after-migration.xml")).write_bytes(raw)
after=lines(raw)
old,new=before[0].split("\t"),after[0].split("\t")
assert new[0]=="tidy-room-13" and new[22]=="1"
assert old[1:15]==new[1:15] and old[16:22]==new[16:22],"Progress/lighting/cleaning changed"
archives=set(new[23].split(","))
assert archives=={"washer","bathvanity","toilet","bathtub","mirror","laundrybasket"}
rows={r.split("\t")[0]:r.split("\t") for r in after[1:]}
for line in before[1:]:
    values=line.split("\t");current=rows[values[0]]
    assert values[10]==current[10]
    assert all(abs(float(a)-float(b))<=.000001 for a,b in zip(values[1:8],current[1:8])),values[0]+" pose changed"
    if values[0] in archives:assert current[9]=="true" and current[8]=="-"
    else:assert values[8:]==current[8:],values[0]+" state changed"
def stock(field):
    return {entry.split(":")[0]:entry.split(":")[1].split(",") if entry.split(":")[1] else []
        for entry in field.split(";")}
stocks=stock(new[15])
for box,ids in stock(old[15]).items():
    assert [i for i in stocks[box] if not i.startswith("gaming")]==[i for i in ids if i not in archives]
assert not archives.intersection(i for ids in stocks.values() for i in ids)
added=set(rows)-{r.split("\t")[0] for r in before[1:]}
assert len(added)==12 and all(i.startswith("gaming") for i in added),added
active=[r[10] for id,r in rows.items() if id not in archives]
assert len(active)==len(set(active))
receipt=dict(device=device,version=13,originalRows=len(before)-1,newModels=12,
    archivedDuplicateRows=sorted(archives),originalPosesPreserved=True,
    bathroomPosesPreserved=True,progressionPreserved=True,otherInventoryPreserved=True,
    uniqueActiveAssets=True,nativeTests={})
if "--save-only" in sys.argv:
    print(json.dumps(receipt,indent=2));sys.exit(0)
methods=[
    ("GamingNativeRuntimeTest","twelveNativeModelsHaveMeasuredBoundsAndIndependentBodyTargets"),
    ("GamingNativeRuntimeTest","doorlessArchitectureIncludesContinuousFloorAndNoOldEntryDoor"),
    ("GamingNativeRuntimeTest","actualDeskCarriesEquipmentFacesCaptionAndMouseCanMoveReleaseReload"),
    ("BathroomNativeRuntimeTest","annexLoadsAndEveryFixtureHasNativeGeometryAndIndependentBodyHits"),
    ("BathroomNativeRuntimeTest","actualBathroomModelsMoveFaceCaptionCancelAndReload"),
    ("HomePlayNativeRuntimeTest","captionFacingUsesLiveRoomYawAndResetsOnEveryDrag"),
    ("CleaningPlayNativeRuntimeTest","jellyMochiAndTumblerBodyMeshesMoveAndKeepTheirPlay"),
    ("CleaningNativeRuntimeTest","dirtyHouseCleansUnlocksAndRetainsNativeResources"),
]
if "--retry-failed" in sys.argv:
    prior=json.loads((OUT/(device+"-verification.json")).read_text())
    assert prior["device"]==device
    receipt["nativeTests"]=prior["nativeTests"]
    methods=[(group,method) for group,method in methods
        if not receipt["nativeTests"].get(method,{}).get("passed",False)]
    assert methods,"No failed or unfinished native checks to retry"
for group,method in methods:
    previous=save()
    subprocess.run(["pico-cli","shell","input","keyevent","224","--device",device],check=True,stdout=subprocess.DEVNULL)
    r=subprocess.run([ADB,"-s",device,"shell","am","instrument","-w","-e","class",
        PACKAGE+"."+group+"#"+method,PACKAGE+".test/androidx.test.runner.AndroidJUnitRunner"],
        capture_output=True,text=True,timeout=240)
    (PRIVATE/(device+"-"+method+".log")).write_text(r.stdout+r.stderr)
    current=save()
    passed=r.returncode==0 and "OK (1 test)" in r.stdout and "FAILURES!!!" not in r.stdout
    receipt["nativeTests"][method]=dict(passed=passed,saveBytesUnchanged=previous==current,
        saveSHA256=hashlib.sha256(current).hexdigest())
    (OUT/(device+"-verification.json")).write_text(json.dumps(receipt,indent=2))
    print(device,method,"PASS" if passed else "FAILED","save unchanged",previous==current,flush=True)
    if not passed or previous!=current:
        print(r.stdout+r.stderr,flush=True);raise RuntimeError("Native regression or save preservation failed")
assert len(receipt["nativeTests"])==8
receipt["relaunchPending"]=True
(OUT/(device+"-verification.json")).write_text(json.dumps(receipt,indent=2))
