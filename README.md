# 拾间 · TidySpace

使用 PICO Spatial SDK 开发的 Android/Kotlin 空间小屋应用。当前默认运行于
Shared Space，进入后直接布置开放式房间，并支持切换到 Full Space。

## 功能

- 抓取、移动和旋转家具与小物；释放时处理支撑关系、碰撞和靠墙摆放。
- 无需打扫即可开始布置；旧存档自动跳过未完成的清洁，保留家具、库存和进度。
- 在物品目录点击「进入全屏空间」进入 Full Space，点击「返回共享空间」退出。
- 关闭 Full Space 的系统窗口后回到 Shared Space，保留当前页面和未完成的操作。
- 多间个人小屋分别保存，可新建、重命名、复制和管理。
- 12 套样板间供浏览和新建小屋使用。
- 奶油色物品目录与小屋管理界面。
- 启动加载动画：模型及位置校准完成后统一显示整个房间，失败可重试。
- 家具、库存、小屋和进度保存在应用私有 SharedPreferences 中。

## 环境

| 项目 | 当前配置 |
| --- | --- |
| Spatial SDK | 6.1.9 |
| JDK | 21 |
| Android compile / target SDK | 35 |
| Android min SDK | 35 |
| ABI | arm64-v8a |
| 应用 ID | com.example.tidyspace.pc5853b2a |
| 默认容器 | DefaultWindowContainer / Volumetric / Shared Space |
| 全屏空间 | TidyFullSpace / Mixed Stage |
| 容器尺寸 | 6 × 4.5 × 6 米 |

需要 Android SDK 和对应版本的 PICO Spatial SDK 开发环境。依赖仓库配置在
`settings.gradle.kts` 中，Gradle Wrapper 已包含在项目中。

本机 SDK 路径写入未跟踪的 `local.properties`，例如：

```properties
sdk.dir=/absolute/path/to/Android/sdk
spatial.tools.dir=/absolute/path/to/PICO/sdk
```

## 构建

在项目根目录执行：

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

确保 `JAVA_HOME` 指向 JDK 21。可安装的调试 APK 输出到：
`app/build/outputs/apk/debug/app-debug.apk`。

使用 pico-cli 安装到选定设备：

```sh
pico-cli device list
pico-cli app install app/build/outputs/apk/debug/app-debug.apk --replace --device DEVICE_ID
pico-cli app launch com.example.tidyspace.pc5853b2a --activity .platform.LaunchActivity --device DEVICE_ID
```

仅在应用 ID 和签名兼容时覆盖安装，才能保留原存档；卸载会删除应用数据。
当前真机正式版使用独立发布证书，本地 debug 签名不能覆盖它。签名不兼容时使用
独立 `.preview` 包验证，正式更新交由原签名者签署 release APK，禁止卸载绕过。

## 目录

- `app/src/main/java/`：界面、业务规则、持久化与 ECS 场景。
- `app/src/main/assets/`：运行模型、材质、音频和样板间资源。
- `app/src/main/res/`：图片、品牌图标和 Android 资源。
- `app/src/test/`：JVM 回归测试。
- `app/src/androidTest/`：设备原生验证与界面检查。
- `scene/`：Blender 模型源文件、美术生成与验证脚本。
- `branding/`：选定的品牌图标与说明。
- 根目录设计/验证 Markdown：历史实现记录，涉及旧版本的数量或尺寸应以当前源码为准。

生成的 APK、构建缓存、设备存档、运行截图、编辑器缓存和本机配置不纳入 Git。

## 验证

2026-11-24 的构建、Lint 和 355 项 JVM 测试通过，涵盖旧存档直接进入布置、
多小屋及撤销快照迁移、清洁重开动作失效和空间模式状态管理。
已修复旧 Stage 延迟销毁回调关闭新 Full Space 的竞态，空间状态以系统反馈为准。
设备验证使用独立 preview 包，9 项原生检查全部通过；检查范围和发布注意事项见
[更新记录](CHANGELOG.md)。
Full Space 是可选的空间模式，未据此承诺帧率提升。

原生断言和截图不替代实际佩戴时的手势、舒适度与性能体验。
