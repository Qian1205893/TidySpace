# 拾间：轻松清洁迭代验证

本次依据用户最新反馈：Volume 切回 Fixed，搬动物件保持重力竖直（墙刷除外），清洁从繁琐的小块任务合并为自然大片区域。Shared Space 和右侧 Augment 保留。

- 安装 SDK 6.1.9 的公开 `WorldScaleType.getValue()` 实际结果：AUTOMATIC=0、DYNAMIC=1、FIXED=2。Manifest 使用 2。
- 108 项 JVM 单测通过，覆盖家具及承托物竖直、垃圾竖直、墙刷自由转动、一次轻扫完成大片区域、工具分工、空中/跳跃不清洁、接触段比例、垃圾投放、解锁、重玩及 v5→v6 迁移。
- APK、设备测试 APK 构建成功；Android Lint 通过。
- SpatialUI 样式验证 0 errors、0 warnings；`PicoTheme`、原生组件和右侧 Augment 保留，清洁完成音改用原有音乐音符。
- GLB 校验 0 errors、0 warnings；122 nodes、65 个必须可寻址节点均存在、117948 triangles、12222952 bytes。
- 6 片清洁区域及 3 件垃圾的领域坐标与 Blender 导出坐标逐一匹配。
- 灰尘和墙面烟尘使用随机渐变 RGBA 纹理及 GLB `alphaMode=BLEND`，表面贴合地板/墙体，轮廓与底色透明混合；可寻址的 8 段连续区域支持扫净揭露，无缩放污渍动画。
- 真机安装前导出最新 `shared_prefs/tidy-room.xml`，备份为 `.scratch/relaxation-headset-preinstall.xml`。生产 `RoomPersistence` 已实际解码这份 v5 备份并完成 v6 往返：21 件物品、玩具历史、机关、姿态及清洁进度保留；墙面合并进度为 0.78885674 和 0.33333334。
- 使用 `pico-cli app install --replace` 覆盖安装；设备验证使用直接 `am instrument`，避免 Gradle UTP 卸载目标应用。

- 真机直接设备测试 `OK (1 test)`，44.629 s，实际核验 Fixed 元数据、建筑/工具原生网格、六片污迹逐段隐藏、垃圾投放、完整清洁解锁、家具添置、保存、重玩和原生资源释放。
- 覆盖安装与真机测试之后，最新存档与安装前逐字一致，SHA-256 均为 `d9549755b5fa6d6bc16b87cabc70180ef7cc7eb188635394d5ad730c06cdc421`，21 件物品未改变。
- 真机应用进入 `ready furnishings=21 targets=84 shared=true`。尚未输出渲染边界，使用已有尺寸包络兜底，half=(1.0799999,1.0799999,1.0799999)，scale=0.53571427；该窗口已由测试验证为 Fixed。
- 修正 `SharedVolume` 历史日志的 Dynamic 文案为 Fixed 后，最终 APK 再次构建、覆盖安装与启动成功；最终安装前后的存档再次逐字一致，21 件物品和进度保留。此追加仅改日志及注释，未改清洁或空间计算行为。
- 空间截图两次均因全黑而被 CLI 拒绝（non-black pixels 0.000%），包括发送正常唤醒键之后；不将其作为有效实机截图。可用模型预览为 `scene/renovation/dirty-house.png`，它是 Blender 预览。最终眼手触感与头显可见画面仍需佩戴后试玩确认。

自动化只验证应用与原生场景行为，不能替代用户真实眼手触感试玩。
