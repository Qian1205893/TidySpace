# Hozy 参考图：房间建筑更新

用户参考：https://www.bilibili.com/video/BV1JPjJ6FEJf/ 及同轮上传的截图。
网页标题可确认视频是 Hozy；本次未读取到可审查的视频帧，以上传截图为视觉依据。

## 已实现

`RoomArchitecture.kt` 创建前方、右侧、顶部开放的房间。地板顶面位于工作台局部
Y=-0.32m，桌面支撑保持 Y=0.0175m，避免改动已验证的摆放、吸附和碰撞规则。
两面墙通过分段网格形成真正的开窗孔洞；桌腿、抽屉立面、窗框、窗台、层板、
墙板和地板都使用真实三维几何。五关仍共用房间，后两关保留独立垂直收纳板。

人字拼地板采用裁剪到固定边界的独立板块几何，按六种木色合批；建筑合计
16 个材质批次。资源仍在创建时分步注册，在场景销毁时统一关闭。
使用 SDK 6.1.9 公开的 DirectionalLightComponent 和 GroundShadowComponent。
顶部操作区由 304dp 高缩至 168dp，删除常驻槽位标签以减少遮挡。

## 验证证据

- 托管 Codegen 使用 JDK 21，`assembleDebug` 完成并产出 APK。
- 已安装至 `emulator-5554`，应用进程启动并完成 `scene-ready level=0 objects=6 slots=6`。
- 四条桌腿实际视觉边界与独立地板顶面误差约 `2.98e-8m`，均通过。
- 运行时记录 `room-ready open-front open-roof floor=1.50x1.12 walls=2 window-apertures=2`。
- 当前进程的 crash 日志为空。
- 场景源代码检查通过；SpatialUI 样式检查为 0 errors、0 warnings。
- 本次重新运行 `testDebugUnitTest`：25 项通过，0 失败、0 错误、0 跳过。
- `tidy-room-emulator-buffer.png` 为实际 2048×2046 PNG，人工检查确认完整房间、
  通透窗洞、落地桌腿、真实阴影、首关物件及未被大面板遮挡的场景。
- 模拟器的 `screencap --spatial` 不受此 ROM 支持；改用普通模拟器显示缓冲捕获，
  本张图片确实包含应用空间图层，不是根据文件存在就推断场景显示成功。

## 未完成与流程边界

精细家具生成根流程 `run-4c9d5180-8166-4c79-966e-6dd8285b00f0` 返回
`SGS_SUPPLY_POLICY_REQUIRED`：生成模型须提供明确供给策略和 `maxSubmissions` 次数预算。
未授权生成次数，未发起模型生成任务，未交付 Editor bundle。
只读工具检查还显示当前 SGS 托管 CLI 未安装。此前 Editor 两次会话启动超时的记录保留。

建筑 Codegen 根流程 `run-b22bce83-dbac-4925-92ad-aefc0446b6b1` 的构建完成，
但终态归档为 blocked：该后端只自动验证 `scene_generated` 和 `project_builds`，
无法验证自定义的 `room_geometry` 条件。这里保留原始失败状态；
房间视觉与支撑检查以上述独立截图及运行时日志为证，不将其冒充托管流程通过。

当前材料没有真实 PBR 纹理贴图，原三十件小物仍为简化模型。暖色光照与阴影有实际
运行时证据，但柔和接触阴影、复杂曲面和精细磨损尚未达到参考视频的品质。
仅首关完成视觉检查；真实头显的眼手拿取、双手旋转、可读性与舒适度仍待设备验证。
墙面为虚拟墙面，未新增现实墙面识别或空间持久锚定。

当前 APK：`app/build/outputs/apk/debug/app-debug.apk`，33,002,901 字节，
SHA-256：`00359485d0e459bc048a31115446f8382424081ecc127d71fdd8d6a28fc14f5a`。
