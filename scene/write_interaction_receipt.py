"""Collect completed build/device checks into the delivery receipt."""
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/"interaction-refresh-20261115"
apk=OUT/"TidySpace-interaction-refined.apk"
digest=hashlib.sha256(apk.read_bytes()).hexdigest()
devices={name:json.loads((OUT/f"{name}-delivery.json").read_text()) for name in ("headset","emulator")}
for result in devices.values():
    assert result["apk_sha256"]==digest and result["apk_matches"]
    assert result["ready"] and result["save_unchanged"]
    assert not result["fatal"] and not result["scene_failure"]
native={device:json.loads((OUT/f"{device}-native-regression.json").read_text())
    for device in ("PB3310PGL7200091B","emulator-5554")}
assert all(check["passed"] for checks in native.values() for check in checks.values())
suites=[ET.parse(path).getroot() for path in (ROOT/"app/build/test-results/testDebugUnitTest").glob("TEST-*.xml")]
tests={key:sum(int(suite.attrib[key]) for suite in suites) for key in ("tests","failures","errors","skipped")}
assert tests["tests"]==199 and tests["failures"]==tests["errors"]==0
windows=json.loads((OUT/"window-aperture-check.json").read_text())
assert all(check["open"] for check in windows)
assert (OUT/"emulator-stable.png").is_file()
receipt={
    "apk":apk.name,"sha256":digest,"unitTests":tests,"devices":devices,"nativeChecks":native,
    "windowApertures":windows,"rotationGain":1.75,"pairTranslation":False,"itemSizes":"fixed",
    "dragFacing":"one time toward the HMD position, gravity upright",
    "visualEvidence":{"emulator":"emulator-stable.png","headset":"unavailable: capture rejected as all black",
        "offlinePreview":["room-art-preview.png","materials-detail.png"]},
}
(OUT/"delivery-receipt.json").write_text(json.dumps(receipt,ensure_ascii=False,indent=2))
print(json.dumps({"apk":str(apk),"sha256":digest,"tests":tests,"savesPreserved":True,
    "nativeChecksPassed":sum(len(checks) for checks in native.values())},indent=2))
