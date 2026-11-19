# 掌心游乐 · 交付与验证

日期：2026-11-14。SDK 6.1.9，验证设备仅为 `emulator-5554`。

## 本轮交付

新增八种可重复添置的手把件：拨浪鼓、精灵球、团子捏捏、果冻方块、
六泡彩虹板、指尖陀螺、企鹅不倒翁、咔哒旋钮。保留木鱼、音乐盒、
清音钵，合计十一种。每个实例具有独立的机关、计数和可持久状态。

- 拨浪鼓：抓柄往复转动，过阈值才触发鼓点；两颗珠子摆向鼓面。
- 精灵球：正面按钮控制真实铰链；开盖显示星光，检查上方、后方及房间边界。
  添置时优先寻找能开盖的空位。
- 捏捏：持续捏合压缩，双手靠近可加深压缩；只缩放软体子节点。
  团子回弹较快，果冻较慢，底座与承托基准保持稳定。
- 按泡：六颗独立翻转；全按下完成一轮，全部弹回后才能再获得整轮反馈。
- 陀螺：拨动后有有限惯性，逐渐停下；叶片交互目标随转子转动。
- 不倒翁：推斜后阻尼摇摆，回到直立；弧底补偿保持接地。
- 旋钮：有方向的十二档位，每跨一档反馈，处理跨档浮点误差。

界面为真正的 `Augment`：`NormalizedPoint3D.Right`、
`CenterLeft`、32dp 间距，640×960dp，圆角 32dp。
关闭该 Augment 的系统玻璃后绘制奶油白底；薄荷色选中态、原生分段控件、
原生按钮，以及实际 Blender 模型渲染的十一张缩略图。主窗口继续使用 Shared Space。
面板不挂在房间节点上，因此不随底座模型自转。原后墙教学板及其摆放禁区已移除。

默认“手把件”，另有“布置”和“手札”；每页六件、两列，保留原五关入口、
十一段引导、委托、成就、三种氛围、撤销、重做、复制和收回。

## 已执行验证

- APK 与 AndroidTest APK 构建成功。
- **88 项单元测试通过，0 失败**。包含新增十二项手把件测试，
  覆盖取消、重复释放、无效机关、抖动、跨档、开盖避碰、独立实例、
  按泡整轮、持久化、撤销及旧存档迁移。
- 设备内 `RoomNativeRuntimeTest` 已通过，覆盖全部十一种模型的原生加载，
  八种新机关的领域输入到原生节点变形、软体恢复、声音资源、
  存档和克隆／移除后的交互目标释放。测试通过生产 ViewModel、UseCase、
  RoomFurniture 和 PlaythingAnimator 执行，不模拟真实追踪手势。
- Khronos glTF Validator：新 GLB **0 错误、0 警告**。
  信息级提示为部分材质未使用 UV / tangent。
- SpatialUI 样式校验：**0 错误、0 警告**。使用 app-owned 配色，
  未改写原生 ColorScheme，按钮内容继承组件前景色。
- 实际应用日志显示 24 件测试布置物、79 个交互目标，Shared Space 已就绪。
  运行图能看到新增玩具、开启的精灵球、右侧 Augment 和移除教学板后的后墙。
- 最终设备测试用时 40.775 秒，`OK (1 test)`。通过公开 Android
  `WindowInspector` 取得实际已合成的 1280×1920 Augment 内容并绘制为 PNG；
  已检查标题、缩略图、六件卡片、分页与底部操作，没有文字或控件裁切。
  窗口根为主窗一个、Augment 一个及反馈浮字五个。

## 产物

- `app/build/outputs/apk/debug/app-debug.apk`
- `scene/playthings/TidySpace-playthings.blend`：可编辑模型源文件
- `scene/build_playthings.py`：模型与短音效生成程序
- `scene/render_plaything_icons.py`：实际资产缩略图生成程序
- `scene/playthings/blender-inspection.png`：Blender 离线材质预览
- `app/src/main/assets/models/tidy-playthings.glb`：实际运行资产
- `PLAYTHINGS-DESIGN.md`：本轮设计和三项机制研究来源
- `playthings-runtime.png`：24 件物品的真实测试布置运行图
- `playthings-augment-panel.png`：实际原生 Augment 的完整内容绘制图
- `scene/playthings/native-validation.log`、`gltf-validation.json`、
  `style-validation.log`：原始验证结果

测试布置不替换用户进度；结束时已恢复本轮开始前的原始房间存档，并重新启动最终 APK。
从右侧“手把件”页选择即可添置新玩具。

最终 APK SHA-256：
`68f7b3cda37553c5544a0c7516860fd640725dd78f24f6c42047944bb8a7dce5`

## 验证边界

模拟器 ROM 缺少 `uiautomator spatial`、`pvrtracking_inject` 和空间 FOV
截图支持。常规 PNG 已人工查看，包含真实场景，但当前取景会裁掉部分右侧面板；
未把自动点击或自动转头列为已通过。原生窗口内容绘制只用于检查界面布局，
不能替代空间手势与注视验证。

真机眼手追踪、触感与长时间舒适度尚未试玩。物体落点使用承托／碰撞求解，
捏捏使用有限软体变形，不代表压力传感或完整弹性体仿真。
离线 Blender 预览与 PICO 实时光照有差异。
