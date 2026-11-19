# Blender 家具与材质升级

2026-11-14。针对“模型质感不够精细”的修订；保留 Shared Space、
自然抓取、手腕/双手旋转、搬家开箱与原五关。

## 方案

Three.js 是浏览器三维渲染库。当前应用使用 PICO 原生 Entity 渲染，
更换为 Three.js 不会自动增加模型细节，还会引入另一套渲染与交互集成。
本轮采用 Blender 4.5.16 LTS 制作原创网格与贴图，导出原生引擎支持的 GLB。

- 13 件家具/小物：圆润沙发和坐垫、缝边、弯木椅背与独立椅条、
  细木作桌面与抽屉、椭圆茶几、三块层板、包边流苏地毯、褶皱灯罩、
  木箱内壁与铰链、曲面叶片、带卡纸的画框、书页与书脊、内外杯壁与杯柄。
- 木材、织物、地毯、陶釉等使用导出的 base color / normal / roughness 图像。
  GLB 内嵌所有贴图；不依赖 Blender 的程序噪声节点或外部网址。
- `desk_drawer`、`crate_lid`、`lamp_bulb`、`lamp_switch` 保持独立节点。
  Kotlin 使用原有领域状态更新机制，不把可动零件合并进家具本体。
- 每件家具具有分部位碰撞区域，保留开放书架和木箱的空腔。
  新书架中层有对应的承托高度和“放下后随书架移动”回归测试。
- 房间木色的明暗差缩小，增加低强度冷色补光，改善阴影中的可辨识度。

## 文件与再生成

- `scene/build_furniture.py`：原创参数建模、UV、材质、纹理、GLB 导出和检视渲染。
- `scene/furniture/TidySpace-furniture.blend`：可编辑 Blender 源文件，附检视布局与灯光。
- `scene/furniture/blender-inspection.png`：离线模型检视图，不是应用运行截图。
- `scene/furniture/manifest.json`：13 件家具的导出边界，单位米、Y 轴向上。
- `app/src/main/assets/models/tidy-furniture.glb`：应用实际加载的模型。

```sh
/Applications/Blender.app/Contents/MacOS/Blender \
  --background --python scene/build_furniture.py
```

GLB 的家具根节点在原点，运行时应用保存的布局；`.blend` 中的预览摆放不改变
游戏初始状态。四件开箱小物在游戏中仍从箱内取出。
全体静态零件按家具/机制组归并，以减少 Entity 数量；
原始参数仍在生成器中可编辑。所有资源按场景生命周期释放。

## 依据

- [Three.js Fundamentals](https://threejs.org/manual/en/fundamentals.html)
- [Blender glTF 2.0 导出](https://docs.blender.org/manual/en/4.5/addons/import_export/scene_gltf2.html)
- 当前安装的 PICO SDK 6.1 公共文档：`spatial-sdk_resource-management_model.md`，
  `spatial-sdk_ecs_manage-entity-hierarchies.md`。

## 验证

- Gradle 构建成功。45 项领域/交互/持久化测试通过，0 失败、0 错误、0 跳过。
  新增的中层书架承托与随动测试通过；最终仅场景适配修订后再次构建成功。
- Khronos glTF Validator：0 errors、0 warnings。57 条 information 为无贴图
  材质上的未使用 UV/切线等提示；完整报告未截断，见
  `.scratch/furniture-gltf-validation.json`。Blender 最终导出无错误/警告。
- GLB 为 16,758,240 bytes、104,174 三角形、17 个网格、34 个节点、
  19 张内嵌图像。已逐字节确认 APK 中 GLB 与源资产一致，且不压缩存储。
- SpatialUI 风格检查：0 errors、0 warnings。空间放置源码检查通过；
  静态启发式对边界证据的提示，由下述实际运行边界日志补充。
- 安装并启动于 `emulator-5554`。最终进程 3289：
  `ready furnishings=13 targets=49 shared=true`，13 个家具根节点 scale 均为 1。
  应用 AndroidRuntime 错误日志为空。
- 修正已旋转房间的 AABB 再次放大适配半径的问题。恢复已有布局后适配比例
  从 0.586027 恢复至 0.6897193，保留原来的房间朝向和布置。
  实际模型边界 min=(-0.49513,-0.38676,-0.59750)，
  max=(0.49894,0.38676,0.59426)，在 1.6×1.4×1.6m 窗口内，`fits=true`。
- `furniture-runtime-final.png`：实际 2048×2046 模拟器显示缓冲 PNG，已打开核对
  房间、家具、灯罩、沙发、画框和文字可见。最终日志：
  `.scratch/furniture-runtime.log`。

最终 APK：49,958,195 bytes；
SHA-256：`7dfffb57465d31f68b06b6ab2eaa455ac83097bb3b383d11b827842e6bcd392b`。

## 交付边界

Blender 离线灯光与 PICO 实时渲染不同，以实际运行截图为应用外观证据。
Shared Space 使用系统环境光，实时阴影、织物与金属反射仍与离线检视图有差异。
当前模拟器 ROM 不支持此前尝试的 spatial screencap，且缺少
`/system_ext/bin/pvrtracking_inject`；本次使用包含应用空间图层的普通截图，
未把它称为真机 FOV，也未假称已完成相机绕拍或自动化手势验收。
自然抓取的代码路径与规则测试保留，真实眼手、双手操作和真机性能仍待实测。
本轮精细模型覆盖布置模式的 13 件家具/小物，原五关三十件收纳物的美术未重制。
