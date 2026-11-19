# 小屋双手旋转

双手在小屋旁同时捏合，像转托盘一样水平转动双手，小屋会随双手连线转向。
不需要选中地板、墙面或底座。松开任一只手立即停止；双手都松开后再捏合可继续旋转。

单手仍用于拿取家具、垃圾和工具。双手捏住同一件物件时保留其双手玩法。
拿着物件时，另一只手在空处捏合会先取消物件的当前操作并恢复原位，然后转动小屋。
双手靠得太近或上下重叠时暂停旋转，恢复有效间距后重新建立基线，避免突然跳角度。
丢失追踪或关闭场景会取消本次旋转。

旋转输入使用 SDK 公共 `inputDevicePose.rawPosition`，转换到不随小屋转动的
`fittedRoot` 坐标系，只计算水平转角，不改变小屋的位置、尺寸或倾斜。
`RoomScreen` 接收未限定命中实体的空间指针事件；
`RoomRenovation` 已移除底座的旋转碰撞入口。
Fixed 模式、右侧 Augment、清洁阶段和现有物件玩法均沿用现有结构。

验证：121 项单元测试通过，其中新增 13 项覆盖单手背景、双手空处旋转、松手立即停止、
重新捏合、丢失追踪、退化间距、无效位置、跨角度边界、物件优先和清洁阶段。
模拟器原生清洁流程测试通过，并确认场景命中目标中没有 `@room`。
Android Lint 通过；SpatialUI 样式检查为 0 errors / 0 warnings。

模拟器和头显存档在首次覆盖安装及原生测试前后逐字节一致，场景均加载 21 件物品。
最终安装包也已覆盖两端，两端存档仍与各自备份逐字节一致。最终进程均输出
`ready furnishings=21 ... rotation=two-hand-pinch floor-wall-rotation=false`，
当前进程崩溃日志为空。APK SHA256 为
`741649b51ff1c849865df8d8643b4ab94355e2ce7cccd6c51d21ebafaa0fcba1`。
自动验证没有注入真实双手追踪；佩戴后的手势手感仍需实际试玩确认。

模拟器的 `screencap --spatial` 返回 `unrecognized option --spatial`，
因此使用普通合成画面截图，并人工确认其中包含小屋和双手操作提示。

UI 仅调整现有提示文字，沿用 `PicoTheme`、SpatialUI `Text` 与原生控件。
`PlayPalette` 的 cream `#F1EEE7`、mint `#B8E5D2`、ink `#29302C`、
secondary `#66716A`、soft `#E4E0D7` 未改动，原生 ColorScheme 未替换。
右侧 Augment 继续使用 `enableMaterialBackground=false` 的现有不透明面板；
原有组件颜色、悬停反馈和内容结构未改动。
