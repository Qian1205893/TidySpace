"""Run existing save-safe model checks, waking the HMD before each activity."""
import json
import subprocess
import sys
from pathlib import Path
device=sys.argv[1]
out=Path(__file__).resolve().parents[1]/"visual-refresh-20261115"
if len(sys.argv)>2: out=Path(__file__).resolve().parents[1]/sys.argv[2]
methods=(
    "continuousFramesImmediatelyMoveActualEntityAndRejectStaleSnapshot",
    "firstLevelToolFramesMoveActualModel",
    "extractingPreviewPreservesCapturedNativeEntity",
)
results={}
for method in methods:
    subprocess.run([sys.executable,str(Path(__file__).with_name("verify_art_delivery.py")),
        "backup",device,device,"native-"+method,out.name],check=True)
    subprocess.run(["pico-cli","shell","input","keyevent","224","--device",device],check=True)
    result=subprocess.run([
        "/opt/homebrew/bin/adb","-s",device,"shell","am","instrument","-w",
        "-e","class","com.example.tidyspace.pc5853b2a.RoomDragNativeRuntimeTest#"+method,
        "com.example.tidyspace.pc5853b2a.test/androidx.test.runner.AndroidJUnitRunner",
    ],capture_output=True,text=True,check=True)
    (out/(device+"-"+method+".log")).write_text(result.stdout+result.stderr)
    passed="OK (1 test)" in result.stdout and "FAILURES!!!" not in result.stdout
    results[method]={"passed":passed,"device":device}
    print(method, "PASS" if passed else "FAILED", flush=True)
    if not passed:
        raise RuntimeError("Native model check failed: "+method)
(out/(device+"-native-regression.json")).write_text(json.dumps(results,indent=2))
