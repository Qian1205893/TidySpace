# 清洁小屋验证记录

2026-11-14。当前 APK 已重新安装并启动于 PICO B3110；包名 com.example.tidyspace.pc5853b2a。用户可以从脏屋开始操作，工具位于地板前沿，垃圾桶位于右前方。

验证结果：

- 102 项 JVM 测试通过，包含 12 项清洁流程测试。覆盖工具分工、接触/实际位移、倒置工具、远距离跳变、桶口投放、取消回滚、48 处全部清洁、添置解锁、重玩、存档迁移和阳台/壁炉碰撞。
- 1 项 B3110 真机设备集成测试通过，报告为 `app/build/outputs/androidTest-results/connected/debug/TEST-B3110 - 16-_app-.xml`。实际载入原生 GLB、创建工具与垃圾抓取区域、执行全部清洁动作、核对污迹隐藏、工具收起、存档与重玩。该测试通过领域动作驱动，不是眼手追踪模拟。
- assembleDebug、Android Lint、架构检查和 SpatialUI 样式检查通过。`.scratch/verification_summary.json` clean=true；原始截图输入不经过设计 JSON 忠实度检查。SpatialUI 仍使用 PicoTheme 和原生 Button、SegmentControl、Text、LinearProgressIndicator；应用色板为奶油白、薄荷绿、深色文本，右侧 Augment 的玻璃背景明确关闭。
- GLB 校验零错误、零警告，56 个要求节点全部存在。资源为 `app/src/main/assets/models/tidy-renovation.glb`；源文件与脏/净预览在 `scene/renovation/`。142264 个三角形，约 13.4 MB。
- 真机最终日志：ready furnishings=21 targets=93；Dynamic Volume 使用原生测量，scale=0.550059，最终边界 min=(-0.766202,-1.0719999,-0.7397271)、max=(0.97135323,-0.24934459,0.73289126)，half=(1.0799999,1.0799999,1.0799999)，fits=true。底部与 Volume 底面保留约 8 mm；18 mm CaptionBar 间距定义在旋转安全包络上。
- 实际头显画面已检查：`cleaning-headset-delivery.png`。能看到完整阁楼、坡屋顶、窗洞、壁炉、阳台、污迹与清洁工具。Augment 在右侧，但本次全屋视角未覆盖面板全宽。独立补拍面板的手动 instrumentation 被系统以“fully stop ... by user request”终止；未再强行重试。

存档事件与恢复限制：

Gradle connectedDebugAndroidTest 的 UTP 在测试结束后自动卸载了被测应用，连带清理真机本地存档。这是本轮执行中的失误。之后已重新安装，并从模拟器的最近可用真实布置恢复 21 件物品及其姿态、成就、玩具进度；备份保存在 `.scratch/cleaning-recovered-layout.xml`。此前真机日志有 23 件物品，所以头显独有的最近改动未能完整恢复，不宣称原真机存档全部保留。

今后对任何已有用户存档的设备运行 connectedAndroidTest 前，必须先用 run-as 导出真实 SharedPreferences 到主机，保存哈希；测试后重新安装并恢复、比对存档。不要依赖 UTP 保留应用数据。用于补拍的直接 instrumentation 不自动卸载应用，但仍需先备份并避免与用户同时操作头显。

目前未完成真实眼手手感验证；模拟器普通截屏只提供局部模型显示，不能代替真机完整画面。Blender 离线预览比设备实时光照更柔和，不把预览当作最终运行光照证据。
