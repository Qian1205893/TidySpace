# 五张参考图对应的全套模型美术升级

完成暖木、奶油、橄榄绿、灰蓝、芥末黄与砖红的统一配色，并重新导出五组运行模型。范围覆盖 31 件唯一库存物品、保留的旧木箱模型、四只搬家纸箱、房屋与长阳台、清洁工具、固定家居装饰及其机关。没有改动当前 UI、Fixed Shared Space、关卡规则、拖拽链路或用户存档布局。

[整屋美术预览](visual-refresh-20261115/room-art-preview.png)与[材质近景](visual-refresh-20261115/materials-detail.png)是最终 GLB 的离线 Cycles 布置，供看清模型、配色与细节；不是头显截图，也没有把布置写进用户存档。[模型总览](visual-refresh-20261115/model-contact-sheet.png)覆盖家具、搬家物件、掌心玩具和乐器。[模拟器真实画面](visual-refresh-20261115/emulator-final-runtime.png)保留当前清洁阶段及固定视角，房间前缘有裁切。

木材使用顺着构件方向的细纹和区分开的蜂蜜橡木、胡桃木；室内和入口地板改为真实拼缝的人字拼花，阳台保留整墙长度与长木板。沙发使用灰蓝织物和浅色包边，椅垫、脚凳使用芥末黄，靠垫使用砖红，毛毯使用拼布，地毯使用织纹、几何纹样与流苏。陶瓷、纸纤维、橡胶、黄铜和涂漆金属保留不同的粗糙度与反光。

基础色 PNG 明确按编码后的色值写入，避免再转换一次导致木材偏暗、偏橙。法线与粗糙度随 GLB 导入；适合的材料将表面凹隙 AO、粗糙度和金属度打包。表面凹隙 AO 不等于家具与地板的接触阴影，移动中的阴影仍由运行场景处理。

运行画面发现大面积石膏墙的细微法线造成细碎斑点，最终改为干净的哑光矿物漆。最终模拟器画面确认斑点消失，清洁污渍仍按原进度保留。离线预览使用暖窗光与室内补光；实际应用继续使用原有可调日光、壁炉灯和室内灯。

五组 GLB 经 Khronos 验证全部为 0 错误、0 警告，原节点与机关名称没有丢失。全部作者边界与升级前存档的模型边界比对通过，误差阈值为 1 毫米；入口拼花曾多出约 3 毫米，已修回原边界。

| 运行模型 | 三角面 | 绘制 primitive | 文件字节 |
|---|---:|---:|---:|
| tidy-furniture.glb | 104174 | 45 | 7428472 |
| tidy-moving-home.glb | 92348 | 157 | 8416880 |
| tidy-playthings.glb | 199364 | 55 | 8661024 |
| tidy-renovation.glb | 107144 | 100 | 13288968 |
| tidy-toys.glb | 64246 | 20 | 3424664 |

总绘制 primitive 保持 377，三角面由 587624 减为 567276。GLB 总字节由 65964348 减为 41220008，约减少 37.5%。纹理解码内存属于静态估算，文件体积减少不能直接等同于 GPU 内存或帧率提升；本轮没有声称完成 90 fps 性能认证。详细数据见 [asset-validation.json](visual-refresh-20261115/asset-validation.json)。

193 项单测、Debug APK、测试 APK 和 Android Lint 均通过。最后的石膏墙修正仅改变材料，重新进行了资产检查与 APK 构建；最终 APK 内五组模型的字节与被验证的源 GLB 完全一致。

最终 APK 的三项头显原生检查全部通过：

- 连续 90 帧直接改变椅子的原生 Entity，旧状态不能回写，双手水平旋转保持中心，松手结束捕获；并核对长阳台原生边界。
- 吸尘器、扫把、墙刷分别完成 60 帧原生模型位移。
- 开箱预览原地转为物件，捕获的模型和碰撞子节点保留到松手。

检查使用已有 `RoomDragNativeRuntimeTest` 的三个用例，隔离 ViewModel，不向用户存档写测试布局。第一次整套执行时两项通过，一项在 Activity 启动阶段超时，设备状态为 Asleep；唤醒后该项通过。最终包在每个用例启动前使用公开 Android Wakeup 按键唤醒，三个用例分别通过，结果见 [native-regression.json](visual-refresh-20261115/native-regression.json)。这些是原生 SDK/ECS 自动化证据，没有声称重新佩戴验证了所有物件的眼手手感。

最终已覆盖安装头显 PB3310PGL7200091B 和模拟器 emulator-5554，均进入 ready，当前进程没有 FATAL EXCEPTION 或场景加载、更新失败。每次安装及原生测试前重新备份最新存档，两端各自的最终存档与安装前一致。没有执行会清空持久化数据的旧测试。安装与进度证据见 [headset-delivery.json](visual-refresh-20261115/headset-delivery.json)和[emulator-delivery.json](visual-refresh-20261115/emulator-delivery.json)。

头显未佩戴时的空间截图最初全黑；公开 Wakeup 按键后可截图，但固定 FOV 主要覆盖环境与屋顶，因此不作为完整房间或全部模型的视觉证明。模拟器 6.1 的空间 screencap 不可用，使用当前实例公开 EmulatorController 的只读 framebuffer，没有注入 XR 输入或改变模拟器摄像机。

Spatial Editor 已尝试公开工作流及合理恢复，仍因 ACP 宿主启动失败、运行目录 EACCES 不能创建 Run。本轮沿用已授权的 Blender 作者源与原生 GLB 链路，没有声称获得托管 Editor 验收。

最终 APK SHA-256：`fac6f03d8b863c7cfc8f8defdd2fb76821a752e46f5b234923a87984e343b0f1`。

若需要恢复被替换的旧备份，请先检查 sboxbackup，再打开 Finder 的废纸篓，手动将文件拖回原路径。
