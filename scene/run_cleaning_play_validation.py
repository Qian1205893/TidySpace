"""Official incremental patch gates with current save backup before install."""
import os
import re
import subprocess
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
SCRIPT="/Users/bytedance/Documents/Trae-CN/default/.trae/skills/spatial-design-to-app/scripts/validate_workflow_and_build.sh"
args=["bash",SCRIPT,str(ROOT),"--input-mode","incremental_patch",
    "--generation-mode","existing_module","--profile","patch"]
for path in (ROOT/"app/src/main/java/com/example/tidyspace/pc5853b2a/ui/theme").glob("*Palette.kt"):
    for name,value in re.findall(r"val\s+(\w+)\s*=\s*Color\(0xFF([A-Fa-f0-9]{6})\)",path.read_text()):
        args.extend(["--design-color",name+"=#"+value])
env=os.environ.copy()
env["JAVA_HOME"]="/Users/bytedance/.pico/primer-cli/jdk/jdk-21.0.13+9/Contents/Home"
env["ANDROID_SERIAL"]="PB3310PGL7200091B"
def backup():
    for device in ("PB3310PGL7200091B","emulator-5554"):
        subprocess.run(["python3",str(ROOT/"scene/verify_cleaning_play_delivery.py"),
            "backup",device,"official"],check=True)
backup()
with (ROOT/"cleaning-play-refresh-20261115/workflow-validation.log").open("w") as log:
    p=subprocess.Popen(args,cwd=ROOT,env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1)
    for line in p.stdout:
        log.write(line);log.flush()
        if "[runtime] running ./gradlew" in line: backup()
    status=p.wait()
raise SystemExit(status)
