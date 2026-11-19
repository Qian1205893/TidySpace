"""Package verified delivery files, never preferences or raw user snapshots."""
import hashlib
import json
import shutil
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/"cleaning-play-refresh-20261115"
reports=[ET.parse(p).getroot() for p in (ROOT/"app/build/test-results/testDebugUnitTest").glob("TEST-*.xml")]
tests=sum(int(r.attrib["tests"]) for r in reports)
assert all(int(r.attrib["failures"])==0 and int(r.attrib["errors"])==0 for r in reports)
workflow=json.loads((ROOT/".scratch/verification_summary.json").read_text())
assert workflow["clean"] and workflow["passed"] and not workflow["stale"]
assets=json.loads((ROOT/".scratch/renovation-asset-check.json").read_text())
assert assets["errors"]==0 and assets["warnings"]==0 and not assets["missing"]
allAssets=json.loads((OUT/"all-assets-validation.json").read_text())
assert all(v["errors"]==0 and v["warnings"]==0 and not v["missingNodes"]
    for k,v in allAssets.items() if k.endswith(".glb"))
assert allAssets["toolBounds"]["passed"]
devices={}
apk=ROOT/"app/build/outputs/apk/debug/app-debug.apk"
apkHash=hashlib.sha256(apk.read_bytes()).hexdigest()
for device in ("PB3310PGL7200091B","emulator-5554"):
    delivery=json.loads((OUT/(device+"-final-delivery.json")).read_text())
    checks=json.loads((OUT/(device+"-native-checks.json")).read_text())
    assert delivery["ready"] and delivery["apkMatches"] and delivery["savePreserved"]
    assert len(checks)==5 and all(v["passed"] and v["saveUnchanged"] for v in checks.values())
    for name in ("jellyMochiAndTumblerBodyMeshesMoveAndKeepTheirPlay","dirtyHouseCleansUnlocksAndRetainsNativeResources"):
        assert checks[name]["apkSHA256"]==apkHash
    devices[device]={"delivery":delivery,"native":checks}
shutil.copy2(apk,OUT/"TidySpace-cleaning-and-play.apk")
for name in ("verification_summary.json","design_style_result.json","renovation-asset-check.json","renovation-gltf-validation.json"):
    shutil.copy2(ROOT/".scratch"/name,OUT/name)
result={"unitTests":tests,"devices":devices,"assets":assets,"allAssets":allAssets,"officialWorkflow":workflow,
    "apkSHA256":apkHash,"manualWornValidation":False,
    "previews":"Offline renders imported from the shipped GLB; not headset eye captures."}
result["capture"]=json.loads((OUT/"capture-status.json").read_text())
(OUT/"validation.json").write_text(json.dumps(result,indent=2))
notes=f"""# 清洁与玩具交互升级 · 验证记录

已完成果冻、团子、不倒翁的身体抓取修复，并重做第一关的分段清洁、碎屑聚拢与局部反馈。新版已更新到头显和模拟器，最新存档保留。

## 玩具交互

旧命中将整个身体分配给 `@play`，搬动只依赖很小的底座。本次三个玩具的身体都进入 SpatialUI 原生持续抓取，其他 14 种机关玩具保留原有交互。

捏住软胶身体会压扁软胶子模型，松手回弹；轻推不倒翁会倾斜摇晃。拖远时整件物品搬动并按 CaptionBar 校准正面。双手保持位置固定，松开一只手不跳位，物品根节点不缩放。

## 清洁玩法与质感

积尘与墙污各有 24 个局部纹理区，沿实际工具路径连续减淡。每片污迹只用一个网格，以公开 PBR 材质透明度控制局部擦除；纹理区边缘相互羽化，擦到的区域露出木纹或旧墙原色，未触及区域保留污迹。透明污迹关闭深度写入，避免叠层争抢深度。

扫把将 56 片叶片、纸屑与碎屑聚成两堆；进度到一半后换吸尘器收走。吸尘器同时能清理地板积尘；墙刷有局部泡沫与短暂擦痕；工具刷毛受力时响应，并配有扫地、擦墙和吸入声音。效果使用固定资源池，不增加完成浮窗或彩纸。

局部进度随松手保存，取消抓取回滚本次操作。旧部分进度继续使用，已完成的第一关保持完成；现有布局、库存、成就及游玩次数保留。

模拟器对照确认墙面和壁炉细微法线贴图会产生大片噪点；恢复原设强度后仍有噪点。因此最终仅移除墙漆、灰泥、白色木作和灰缝的细微法线贴图，保留它们的原色、纹理与粗糙度；其他材质恢复原设法线强度。污迹继续独立连续减淡。

## 验证

- {tests} 项单元测试、Android Lint、应用及原生测试 APK 构建通过。
- 官方 incremental_patch / existing_module / patch 全流程通过，未跳过门禁；SpatialUI 风格检查零错误、零警告。
- 全部 6 套最终 GLB 零错误、零警告；清洁模型 {assets["requiredNodes"]} 个必需节点完整，包含 4 个表面网格、96 个独立局部材质、碎屑与刷毛。节点数从叠层版的 770 减至 {assets["nodes"]}，原窗洞保持镂空，刷毛原生边界与工具落地范围一致。
- 头显与模拟器各通过 5 项原生模型回归：三个玩具实际身体命中与逐帧移动、软胶挤压与不倒翁倾斜、完整清洁与再玩、其他 14 种机关玩法、三种工具连续移动和开箱抓取。
- 原生回归期间用户存档逐次按字节比较保持一致；安装后按布局、库存顺序、成就与次数核对保持一致，设备安装包哈希与交付 APK 一致。

原生测试使用实际 SDK Entity、身体网格及公开 SDK 手势帧；不等同于人工佩戴手势实测。最后收尾后重新运行主体与完整清洁两项，结果关联最终 APK 哈希。预览由最终 GLB 离线渲染。最终模拟器运行抓图确认墙面和壁炉噪点已消除，见 `emulator-final-framebuffer.png`。

早期头显空间抓图全黑；最终抓图为可读 PNG，但只包含透视环境，应用不在捕获视野内，因此不能作为本次应用画面验证。人工佩戴实测与佩戴画面对比尚未完成；截图状态另见 `capture-status.json`。

安装包：`TidySpace-cleaning-and-play.apk`

预览：`cleaning-dirty-room.png`、`cleaning-path-foam-heaps.png`、`cleaning-restored-room.png`；局部细节：`cleaning-wall-detail.png`、`cleaning-sweep-detail.png`。

详细机器记录：`validation.json` 与每台设备的 `*-native-checks.json`、`*-final-delivery.json`。

如需恢复旧备份，先检查 sboxbackup，再打开 Finder 废纸篓，手动把文件拖回原路径。
"""
(OUT/"清洁与玩具交互升级.md").write_text(notes)
print(json.dumps({"tests":tests,"nativeMethodsPerDevice":5,"apk":str(OUT/"TidySpace-cleaning-and-play.apk")},ensure_ascii=False))
