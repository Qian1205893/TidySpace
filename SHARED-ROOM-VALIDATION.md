# Shared Space 与布置小屋验证

日期：2026-11-13。设计依据为用户上传的 Hozy 参考截图及
[SHARED-ROOM-DESIGN.md](SHARED-ROOM-DESIGN.md)。

## 本次交付

- Shared Space：`DefaultWindowContainer`，Volumetric，实际物理体积 1.6 × 1.4 × 1.6m。
  源码和 Manifest 不再包含 DefaultStage、Stage 元数据或相机 Anchor。
- 默认布置模式：13 件独立家具和小物、可掀箱盖、可拉抽屉、可切换的灯光、
  三个关系式目标、本地保存。保留五关三十件小物的收纳挑战。
- 直接交互统一使用 `SharedSpatialInput`：同批更新两只手的位姿，单手相对姿态旋转，
  双手中点平移与三维连线旋转；最后一只手释放即结束抓取。
- 家具的零件碰撞体让桌面、开放书架和沙发座面上的小物可以再次被射线选中；
  木箱仅底板、侧板与独立箱盖碰撞，开箱后可选择内部小物。
- 支撑关系保存在领域状态中，家具移动时同时变换后代物件，重新拿起小物可以脱离。
  非法重叠、跨窗洞挂放等恢复合法位置。家具落地时恢复竖直方向，保留用户水平朝向。
- 完成生活目标后仍可继续调整，目标会随布局变化重新判定。

## 构建与测试

命令：`./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain`，
使用本机 JDK 21；最终构建成功。

44 tests，0 failures，0 errors，0 skipped。规则测试通过实际抓取→移动→释放序列
完成全部三个生活目标，再移走杯子确认目标恢复未完成。覆盖：

- 原五关完成、墙面放置、重拿、三维位移、非法输入、切关加载。
- 相对手腕旋转、双手从水平转到垂直、180° 反向、单双手切换无跳跃。
- 家具携物、小物脱离、木箱携带尚未取出的物件、取消恢复整个承载树。
- 碰撞回退、窗洞拒绝、机制交互取消、本地快照校验、循环关系拒绝、
  持久化中的抓取快照、ViewModel 重建与保存失败反馈。

APK：`app/build/outputs/apk/debug/app-debug.apk`，33,140,225 bytes。
SHA-256：`bc7a8ece1740603a65b5735eb7fabce48950f433108d537531ac799e6776b052`。
安装和显式 Activity 启动均成功，目标为 `emulator-5554`。

## 空间与画面证据

首轮运行日志确认：

```text
TidyRoom: ready furnishings=13 targets=50 shared=true
TidyVolume: shared=true half=Vector3(x=0.79999995, y=0.7, z=0.79999995)
            scale=0.6897193 yawSafeRadius=1.0439029
TidyVolume: actualVisualBounds
            min=(-0.5217727,-0.38676012,-0.38934654)
            max=(0.52073807,0.38676012,0.3862428) fits=true
```

`shared-room-ready.png` 是首轮实际 2048×2046 模拟器显示缓冲截图。
人工确认房间与系统环境并存，墙体通透开窗、桌椅、沙发、灯、地毯、箱子与阴影可见。
此轮发现引导文字偏小、画框中的图形越界，随后放大引导并修正画中山形。
`shared-room-initial.png` 为加载期间的系统环境截图，不作为场景成功证据。

第二轮运行进程 PID 18837：`ready furnishings=13 targets=52 shared=true`，
实际模型边界仍为上述数值，`fits=true`。`shared-room-final.png` 确认画框图形修正、
底座与家具完整可见；之后继续将目标正文调为 32sp、标题 40sp、说明 24sp，
并扩大引导区至 1296×296dp。最终文字版本单独重建成功，布局规则检查通过。

最终交付进程 PID 19538 于 17:25:11 记录
`ready furnishings=13 targets=52 shared=true`，窗口模型边界保持 `fits=true`。
[shared-room-delivery.png](shared-room-delivery.png) 为最终 2048×2046 PNG，
已打开核对：玩法文字清晰可读，标题、三个目标、说明与模式按钮完整显示，
房间和家具未被文字遮挡，山形位于画框内。最终 AndroidRuntime 错误日志为空。
一次按包跟随日志未采集到 ready 事件，随后通过按标签日志与实际界面同时确认成功；
未以该跟随命令的退出码代替应用就绪证据。

程序按 SDK 实际坐标转换测量窗口，并保守预留整座房间水平旋转的圆柱边界。
`getVisualBounds` 的数值验证针对 Entity 模型；AttachmentPanel 可读性和可见范围仍需截图
独立检查，不把模型边界结果扩展成所有文字面板均已通过验证。

## SpatialUI

使用原生 `com.pico.spatial.ui.design.Button` 和 `Text`，窗口及 AttachmentPanel 均由
`PicoTheme` 包裹。保留原生 ColorScheme；普通文字使用 `labelPrimary`，
按钮文字继承组件颜色。无自定义 UI 背景、边框、Material 控件或自绘按钮。

默认窗口的 `pico.spatial.windowcontainer.materialbackground=0`，
以透明体积承载房间；不叠加不透明根背景。Entity 使用原生 `HoverEffectComponent`，
Compose 控件沿用内建交互效果。木纹、织物、陶瓷等自定义颜色归属
`SceneResources.roomColors`，用于三维材质，不覆盖 SpatialUI 主题角色。

`verify-design-style.sh app/src/main/java --no-design-colors`：
0 errors，0 warnings，PASS。这里的无设计颜色指没有额外的二维 UI 品牌色。

## 尚未验证与限制

- 当前 ROM 不支持 `screencap --spatial`。普通截图已人工检查包含应用空间图层；
  不将其称作真实头显 FOV 或双眼画面。
- 当前可用模拟器没有可操作的桌面窗口，CLI 不具备可靠空间手势注入入口。
  真实眼手、双手操作、拾取舒适度和完整交互体验仍需 PICO 真机验收。
- 首轮无 AndroidRuntime 崩溃；运行时存在平台 Vulkan pipeline 错误及重复 Transform 注册日志。
  后续修正了自有资源/节点工厂的重复注册，平台图形错误不等价于应用崩溃，
  但未宣称平台日志完全无错。
- 几何细节、程序纹理与阴影已有实际画面，仍不是视频中的高质量资产与光照。
  原三十件收纳物品也保留简化造型。当前首次加载约一分钟。
- 本次公共 Codegen 工作流启动受 `~/.pico/cli/workflow/runs` 的 EACCES 阻止，
  未创建新 Run；没有绕过文件系统权限。应用源码、Gradle 构建、规则测试和模拟器验证
  独立完成，不将这些结果称作托管工作流验收通过。
- SGS 模型生成策略与次数预算未获用户明确授权；未发起相关任务。
  当前实现是本地原创 Kotlin 几何，不是假称已生成或已验收的 Editor 资产。
